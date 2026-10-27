package com.swiftlabs.invoicing.company;

import com.swiftlabs.invoicing.access.privilege.PrivilegeRepository;
import com.swiftlabs.invoicing.access.profile.Profile;
import com.swiftlabs.invoicing.access.profile.ProfileRepository;
import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.issuer.Issuer;
import com.swiftlabs.invoicing.issuer.IssuerService;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Réglages > Sociétés and the companies of each user. Not transactional: each request opens its own session, in the
 * company of the signed-in user, as in the application.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CompanyManagementTest {

    private static final String PASSWORD = "Secret123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private CompanyAccessService accessService;

    @Autowired
    private CompanyWork companyWork;

    @Autowired
    private IssuerService issuerService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private PrivilegeRepository privilegeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> users = new ArrayList<>();
    private final List<Long> profiles = new ArrayList<>();
    private long companiesBefore;
    private String firstCompanyName;
    private String firstCompanyLabel;

    @BeforeEach
    void remember() {
        companiesBefore = companyRepository.count();
        firstCompanyLabel = companyRepository.findById(CompanySchemas.FIRST_ID).orElseThrow().getName();
        firstCompanyName = companyWork.inCompany(CompanySchemas.FIRST, () -> issuerService.getIssuer().getFullName());
    }

    @AfterEach
    void cleanUp() {
        for (Company company : companyRepository.findAll()) {
            if (company.getId() != CompanySchemas.FIRST_ID) {
                jdbc.execute("DROP SCHEMA IF EXISTS " + company.getSchemaName() + " CASCADE");
                jdbc.update("DELETE FROM public.company WHERE id = ?", company.getId());
            }
        }
        jdbc.update("UPDATE public.company SET active = TRUE, name = ? WHERE id = ?", firstCompanyLabel, CompanySchemas.FIRST_ID);
        for (Long id : users) {
            jdbc.update("DELETE FROM public.app_user WHERE id = ?", id);
        }
        for (Long id : profiles) {
            jdbc.update("DELETE FROM public.profile_privilege WHERE profile_id = ?", id);
            jdbc.update("DELETE FROM public.profile WHERE id = ?", id);
        }
    }

    private Profile administrator() {
        return profileRepository.findByName("Administrateur");
    }

    private Profile profileWith(String... codes) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Profile profile = new Profile();
            profile.setName("Profil " + System.nanoTime());
            for (String code : codes) {
                profile.getPrivileges().add(privilegeRepository.findByCode(code));
            }
            Profile saved = profileRepository.save(profile);
            profiles.add(saved.getId());
            return saved;
        });
    }

    private User user(String username, Long companyId, Profile profile) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            User user = new User();
            user.setUsername(username);
            user.setEmail(username + "@example.test");
            user.changePassword(passwordEncoder.encode(PASSWORD), LocalDateTime.now());
            user = userRepository.save(user);
            users.add(user.getId());
            accessService.grant(user, companyId, profile);
            return user;
        });
    }

    private MockHttpSession login(String username) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/perform-login").session(session).with(csrf()).param("username", username).param("password", PASSWORD));
        return session;
    }

    @Test
    void anAdministratorCreatesACompanyAndWorksInItWithoutTouchingTheirCurrentOne() throws Exception {
        user("createur", CompanySchemas.FIRST_ID, administrator());
        MockHttpSession session = login("createur");
        mockMvc.perform(get("/settings/companies/new").session(session)).andExpect(status().isOk());

        mockMvc.perform(post("/settings/companies/create").session(session).with(csrf())
                .param("name", "Atlas Distribution").param("companyType", "COMPANY").param("nif", "000016001234567")
                .param("legalForm", "SARL").param("shareCapital", "100000").param("rcNumber", "16/00-7654321B22")
                .param("taxArticle", "16098765432").param("city", "Oran"))
            .andExpect(redirectedUrl("/settings/issuer"))
            .andExpect(flash().attribute("success", containsString("Atlas Distribution")));

        Company created = companyRepository.findAll().stream().filter(c -> c.getName().equals("Atlas Distribution")).findFirst().orElseThrow();
        Issuer details = companyWork.inCompany(created.getSchemaName(), () -> issuerService.getIssuer());
        assertThat(details.getFullName()).isEqualTo("Atlas Distribution");
        assertThat(details.getNif()).isEqualTo("000016001234567");
        assertThat(details.isCompany()).isTrue();
        assertThat(details.getRcNumber()).isEqualTo("16/00-7654321B22");
        // The company the request started in is untouched
        assertThat(companyWork.inCompany(CompanySchemas.FIRST, () -> issuerService.getIssuer().getFullName())).isEqualTo(firstCompanyName);
        // The creator works there now, with the profile they had
        mockMvc.perform(get("/settings/issuer").session(session))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Atlas Distribution")));
        assertThat(accessService.profileIn(users.get(0), created.getId()).orElseThrow().getName()).isEqualTo("Administrateur");
    }

    @Test
    void identifiersRefusedByTheRulesLeaveNoCompanyBehind() throws Exception {
        user("createur2", CompanySchemas.FIRST_ID, administrator());
        MockHttpSession session = login("createur2");

        // A société without its registre du commerce
        mockMvc.perform(post("/settings/companies/create").session(session).with(csrf())
                .param("name", "Incomplète").param("companyType", "COMPANY").param("nif", "000016001234567")
                .param("legalForm", "SARL").param("shareCapital", "100000").param("taxArticle", "16098765432"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("newCompany", "rcNumber"));

        assertThat(companyRepository.count()).isEqualTo(companiesBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name LIKE 'company_%'",
            Long.class)).isEqualTo(companiesBefore);
    }

    @Test
    void companiesAreRenamedAndDeactivatedButNeverTheCurrentOrTheLastOne() throws Exception {
        user("gestion", CompanySchemas.FIRST_ID, administrator());
        Company other = companyService.create("Autre société");
        MockHttpSession session = login("gestion");

        mockMvc.perform(post("/settings/companies/{id}/rename", other.getId()).session(session).with(csrf()).param("name", "Renommée"))
            .andExpect(redirectedUrl("/settings/companies"));
        assertThat(companyRepository.findById(other.getId()).orElseThrow().getName()).isEqualTo("Renommée");

        mockMvc.perform(post("/settings/companies/{id}/deactivate", CompanySchemas.FIRST_ID).session(session).with(csrf()))
            .andExpect(flash().attribute("error", containsString("dans laquelle vous travaillez")));
        mockMvc.perform(post("/settings/companies/{id}/deactivate", other.getId()).session(session).with(csrf()))
            .andExpect(flash().attribute("success", containsString("désactivée")));
        assertThat(companyRepository.findById(other.getId()).orElseThrow().isActive()).isFalse();
        mockMvc.perform(get("/settings/companies").session(session))
            .andExpect(content().string(containsString("Désactivée")));

        // A deactivated company cannot be opened, even by someone who had access
        accessService.grant(userRepository.findById(users.get(0)).orElseThrow(), other.getId(), administrator());
        mockMvc.perform(post("/companies/switch").session(session).with(csrf()).param("companyId", other.getId().toString()))
            .andExpect(redirectedUrl("/companies/select"));

        mockMvc.perform(post("/settings/companies/{id}/activate", other.getId()).session(session).with(csrf()))
            .andExpect(flash().attribute("success", containsString("réactivée")));
    }

    @Test
    void theCompaniesPageNeedsItsPrivilege() throws Exception {
        user("sansdroit", CompanySchemas.FIRST_ID, profileWith("VIEW_DASHBOARD", "MANAGE_USERS"));
        MockHttpSession session = login("sansdroit");
        mockMvc.perform(get("/settings/companies").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(post("/settings/companies/create").session(session).with(csrf()).param("name", "X"))
            .andExpect(status().isForbidden());
    }

    @Test
    void anAdministratorSetsTheProfileOfAUserInEachCompanyTheyManage() throws Exception {
        Company second = companyService.create("Deuxième");
        Company third = companyService.create("Troisième");
        User admin = user("admin2", CompanySchemas.FIRST_ID, administrator());
        accessService.grant(admin, second.getId(), administrator());
        // The third company: access without the right to manage its users
        accessService.grant(admin, third.getId(), profileWith("VIEW_DASHBOARD"));
        User colleague = user("collegue", CompanySchemas.FIRST_ID, administrator());
        Profile reader = profileWith("VIEW_INVOICES");
        MockHttpSession session = login("admin2");
        mockMvc.perform(post("/companies/switch").session(session).with(csrf()).param("companyId", String.valueOf(CompanySchemas.FIRST_ID)));

        // The form offers the companies the administrator manages
        mockMvc.perform(get("/users/{id}/edit", colleague.getId()).session(session))
            .andExpect(content().string(containsString("Deuxième")))
            .andExpect(content().string(containsString("accesses[1].profileId")));

        mockMvc.perform(post("/users/save").session(session).with(csrf())
                .param("id", colleague.getId().toString()).param("username", "collegue").param("email", "collegue@example.test")
                .param("enabled", "true")
                .param("accesses[0].companyId", String.valueOf(CompanySchemas.FIRST_ID)).param("accesses[0].profileId", "")
                .param("accesses[1].companyId", second.getId().toString()).param("accesses[1].profileId", reader.getId().toString())
                // Forged: a company this administrator does not manage
                .param("accesses[2].companyId", third.getId().toString()).param("accesses[2].profileId", administrator().getId().toString()))
            .andExpect(redirectedUrl("/users"));

        assertThat(accessService.profileIn(colleague.getId(), CompanySchemas.FIRST_ID)).isEmpty();
        assertThat(accessService.profileIn(colleague.getId(), second.getId()).orElseThrow().getId()).isEqualTo(reader.getId());
        assertThat(accessService.profileIn(colleague.getId(), third.getId())).isEmpty();
    }
}
