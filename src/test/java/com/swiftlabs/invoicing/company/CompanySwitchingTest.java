package com.swiftlabs.invoicing.company;

import com.swiftlabs.invoicing.access.privilege.PrivilegeRepository;
import com.swiftlabs.invoicing.access.profile.Profile;
import com.swiftlabs.invoicing.access.profile.ProfileRepository;
import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Signing in and switching company: the user works in one company at a time, with the privileges of their profile
 * there. Not transactional: each request opens its own session in the company of the signed-in user.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CompanySwitchingTest {

    private static final String PASSWORD = "Secret123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private CompanyAccessService accessService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private PrivilegeRepository privilegeRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private Company second;
    private Profile readOnly;
    private final List<Long> users = new ArrayList<>();
    private final List<Long> firstCompanyClients = new ArrayList<>();

    @BeforeEach
    void twoCompanies() {
        second = companyService.create("Deuxième SARL");
        readOnly = new TransactionTemplate(transactionManager).execute(status -> {
            Profile profile = new Profile();
            profile.setName("Lecture factures " + System.nanoTime());
            profile.getPrivileges().add(privilegeRepository.findByCode("VIEW_INVOICES"));
            profile.getPrivileges().add(privilegeRepository.findByCode("VIEW_DASHBOARD"));
            return profileRepository.save(profile);
        });
    }

    @AfterEach
    void cleanUp() {
        for (Long id : users) {
            jdbc.update("DELETE FROM public.app_user WHERE id = ?", id);
        }
        for (Long id : firstCompanyClients) {
            jdbc.update("DELETE FROM " + CompanySchemas.FIRST + ".client WHERE id = ?", id);
        }
        jdbc.execute("DROP SCHEMA " + second.getSchemaName() + " CASCADE");
        jdbc.update("DELETE FROM public.company WHERE id = ?", second.getId());
        jdbc.update("DELETE FROM public.profile_privilege WHERE profile_id = ?", readOnly.getId());
        jdbc.update("DELETE FROM public.profile WHERE id = ?", readOnly.getId());
    }

    private <T> T inCompany(String schema, Supplier<T> work) {
        return CompanyContext.runAs(schema, () -> new TransactionTemplate(transactionManager).execute(status -> work.get()));
    }

    /** A user with the given profile in each company (null: no access there). */
    private User user(String username, Profile inFirst, Profile inSecond) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            User user = new User();
            user.setUsername(username);
            user.setEmail(username + "@example.test");
            user.changePassword(passwordEncoder.encode(PASSWORD), LocalDateTime.now());
            user = userRepository.save(user);
            users.add(user.getId());
            if (inFirst != null) {
                accessService.grant(user, CompanySchemas.FIRST_ID, inFirst);
            }
            if (inSecond != null) {
                accessService.grant(user, second.getId(), inSecond);
            }
            return user;
        });
    }

    private Profile administrator() {
        return profileRepository.findByName("Administrateur");
    }

    private MockHttpSession login(String username) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/perform-login").session(session).with(csrf()).param("username", username).param("password", PASSWORD));
        return session;
    }

    @Test
    void aUserWithOneCompanyGoesStraightIn() throws Exception {
        user("solo", administrator(), null);
        MockHttpSession session = login("solo");

        mockMvc.perform(get("/clients").session(session)).andExpect(status().isOk());
        // Only one company: no switcher in the header
        mockMvc.perform(get("/clients").session(session)).andExpect(content().string(not(containsString("/companies/switch"))));
    }

    @Test
    void aUserWithSeveralCompaniesChoosesThenWorksInTheChosenOne() throws Exception {
        user("multi", administrator(), administrator());
        Client theirs = inCompany(second.getSchemaName(), () -> {
            Client client = new Client();
            client.setType(ClientType.PARTICULIER);
            client.setName("Client de la deuxième");
            return clientRepository.save(client);
        });
        MockHttpSession session = login("multi");

        // No company yet: every page leads to the choice
        mockMvc.perform(get("/clients").session(session)).andExpect(redirectedUrl("/companies/select"));
        mockMvc.perform(get("/companies/select").session(session))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Deuxième SARL")));

        mockMvc.perform(post("/companies/switch").session(session).with(csrf()).param("companyId", second.getId().toString()))
            .andExpect(redirectedUrl("/"))
            .andExpect(flash().attribute("success", containsString("Deuxième SARL")));
        mockMvc.perform(get("/clients").session(session))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(theirs.getName())))
            // The switcher, with both companies
            .andExpect(content().string(containsString("/companies/switch")));

        // Remembered: the next login opens the same company
        assertThat(userRepository.findByUsername("multi").orElseThrow().getLastCompanyId()).isEqualTo(second.getId());
        MockHttpSession again = login("multi");
        mockMvc.perform(get("/clients").session(again)).andExpect(content().string(containsString(theirs.getName())));
    }

    @Test
    void thePrivilegesAreThoseOfTheProfileInTheCurrentCompany() throws Exception {
        user("mixed", administrator(), readOnly);
        MockHttpSession session = login("mixed");
        mockMvc.perform(post("/companies/switch").session(session).with(csrf()).param("companyId", String.valueOf(CompanySchemas.FIRST_ID)));
        mockMvc.perform(get("/clients").session(session)).andExpect(status().isOk());

        mockMvc.perform(post("/companies/switch").session(session).with(csrf()).param("companyId", second.getId().toString()));
        mockMvc.perform(get("/clients").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/invoices").session(session)).andExpect(status().isOk());
    }

    @Test
    void aCompanyThatIsNotOpenToTheUserIsRefused() throws Exception {
        user("onlyfirst", administrator(), null);
        MockHttpSession session = login("onlyfirst");

        mockMvc.perform(post("/companies/switch").session(session).with(csrf()).param("companyId", second.getId().toString()))
            .andExpect(redirectedUrl("/companies/select"))
            .andExpect(flash().attribute("error", "Cette société ne vous est pas ouverte."));
        mockMvc.perform(get("/clients").session(session)).andExpect(status().isOk());
    }

    @Test
    void aFormShownInOneCompanyIsRefusedAfterSwitchingInAnotherTab() throws Exception {
        user("twotabs", administrator(), administrator());
        MockHttpSession session = login("twotabs");
        mockMvc.perform(post("/companies/switch").session(session).with(csrf()).param("companyId", String.valueOf(CompanySchemas.FIRST_ID)));
        // The form carries its company
        mockMvc.perform(get("/clients/new").session(session))
            .andExpect(content().string(containsString("name=\"_company\" value=\"" + CompanySchemas.FIRST_ID + "\"")));

        // Another tab switches to the second company, then the first tab sends its form
        mockMvc.perform(post("/companies/switch").session(session).with(csrf()).param("companyId", second.getId().toString()));
        mockMvc.perform(post("/clients/save").session(session).with(csrf()).param("_company", String.valueOf(CompanySchemas.FIRST_ID))
                .param("type", "PARTICULIER").param("name", "Saisi dans le mauvais onglet"))
            .andExpect(redirectedUrl("/"))
            .andExpect(flash().attribute("error", containsString("autre onglet")));
        assertThat(inCompany(second.getSchemaName(), () -> clientRepository.findAll()))
            .extracting(Client::getName).doesNotContain("Saisi dans le mauvais onglet");

        // Sent from the right company, it is saved
        mockMvc.perform(post("/clients/save").session(session).with(csrf()).param("_company", second.getId().toString())
                .param("type", "PARTICULIER").param("name", "Saisi dans le bon onglet"))
            .andExpect(status().is3xxRedirection());
        assertThat(inCompany(second.getSchemaName(), () -> clientRepository.findAll()))
            .extracting(Client::getName).contains("Saisi dans le bon onglet");
    }
}
