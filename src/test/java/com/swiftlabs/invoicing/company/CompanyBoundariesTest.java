package com.swiftlabs.invoicing.company;

import com.swiftlabs.invoicing.access.privilege.PrivilegeRepository;
import com.swiftlabs.invoicing.access.profile.Profile;
import com.swiftlabs.invoicing.access.profile.ProfileRepository;
import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.backup.BackupDatabase;
import com.swiftlabs.invoicing.backup.BackupFile;
import com.swiftlabs.invoicing.backup.BackupService;
import com.swiftlabs.invoicing.backup.PostgresConnection;
import com.swiftlabs.invoicing.backup.PostgresTools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Where one company's administrator stops: the accounts of other companies, accesses taken away while in use, and
 * the backup of the shared data, which runs with no company at all. Not transactional, as in the application.
 */
@SpringBootTest(properties = "app.backup.enabled=true")
@AutoConfigureMockMvc
class CompanyBoundariesTest {

    private static final String PASSWORD = "Secret123";
    private static final Path FOLDER;

    static {
        try {
            FOLDER = Files.createTempDirectory("swift-shared-backups");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @DynamicPropertySource
    static void folder(DynamicPropertyRegistry registry) {
        registry.add("app.backup.directory", FOLDER::toString);
    }

    @MockitoBean
    private PostgresTools tools;

    @MockitoBean
    private BackupDatabase database;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private CompanyAccessService accessService;

    @Autowired
    private BackupService backupService;

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

    private Company second;
    private final List<Long> users = new ArrayList<>();
    private final List<Long> profiles = new ArrayList<>();

    @BeforeEach
    void secondCompany() {
        second = companyService.create("Société B");
    }

    @AfterEach
    void cleanUp() {
        CompanyContext.setFallback(CompanySchemas.FIRST);
        users.forEach(id -> jdbc.update("DELETE FROM public.app_user WHERE id = ?", id));
        jdbc.execute("DROP SCHEMA " + second.getSchemaName() + " CASCADE");
        jdbc.update("DELETE FROM public.company WHERE id = ?", second.getId());
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

    /** A user with a profile in the first company and/or in B (null: no access there). */
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

    private MockHttpSession login(String username, Long companyId) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/perform-login").session(session).with(csrf()).param("username", username).param("password", PASSWORD));
        mockMvc.perform(post("/companies/switch").session(session).with(csrf()).param("companyId", companyId.toString()));
        return session;
    }

    @Test
    void anAdministratorCannotTakeOverTheAccountsOfACompanyTheyDoNotManage() throws Exception {
        user("admina", administrator(), null);
        User onlyB = user("seulementb", null, administrator());
        User both = user("lesdeux", administrator(), administrator());
        MockHttpSession session = login("admina", CompanySchemas.FIRST_ID);

        // B's own users are not even listed; the shared one is, without the account actions
        mockMvc.perform(get("/users").session(session))
            .andExpect(content().string(not(containsString("seulementb"))))
            .andExpect(content().string(containsString("lesdeux")))
            .andExpect(content().string(not(containsString("/users/" + both.getId() + "/reset-password"))));

        for (User target : List.of(onlyB, both)) {
            mockMvc.perform(post("/users/{id}/reset-password", target.getId()).session(session).with(csrf()))
                .andExpect(flash().attribute("error", containsString("sociétés que vous ne gérez pas")));
            mockMvc.perform(post("/users/{id}/delete", target.getId()).session(session).with(csrf()))
                .andExpect(flash().attribute("error", containsString("sociétés que vous ne gérez pas")));
        }
        // The shared account's email stays as B's administrators set it; its access to A can still be changed
        mockMvc.perform(post("/users/save").session(session).with(csrf())
                .param("id", both.getId().toString()).param("username", "lesdeux").param("email", "pirate@example.test")
                .param("enabled", "true").param("accesses[0].companyId", String.valueOf(CompanySchemas.FIRST_ID))
                .param("accesses[0].profileId", ""))
            .andExpect(redirectedUrl("/users"));
        User after = userRepository.findById(both.getId()).orElseThrow();
        assertThat(after.getEmail()).isEqualTo("lesdeux@example.test");
        assertThat(accessService.companyIdsOf(both.getId())).containsExactly(second.getId());
        assertThat(userRepository.findById(onlyB.getId())).isPresent();
    }

    @Test
    void anAccessTakenAwayOrACompanyDeactivatedEndsAtTheNextPage() throws Exception {
        User worker = user("travailleur", administrator(), administrator());
        MockHttpSession session = login("travailleur", second.getId());
        mockMvc.perform(get("/clients").session(session)).andExpect(status().isOk());

        accessService.revoke(worker.getId(), second.getId());
        mockMvc.perform(get("/clients").session(session)).andExpect(redirectedUrl("/companies/select"));

        accessService.grant(worker, second.getId(), administrator());
        mockMvc.perform(post("/companies/switch").session(session).with(csrf()).param("companyId", second.getId().toString()));
        mockMvc.perform(get("/clients").session(session)).andExpect(status().isOk());
        companyService.setActive(second.getId(), false, null);
        mockMvc.perform(get("/clients").session(session)).andExpect(redirectedUrl("/companies/select"));
    }

    @Test
    void aProfileChangedInTheCompanyAppliesAtTheNextPage() throws Exception {
        User worker = user("reduit", administrator(), null);
        MockHttpSession session = login("reduit", CompanySchemas.FIRST_ID);
        mockMvc.perform(get("/clients").session(session)).andExpect(status().isOk());

        accessService.grant(worker, CompanySchemas.FIRST_ID, profileWith("VIEW_DASHBOARD"));
        mockMvc.perform(get("/clients").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void theSharedDataIsBackedUpWithNoCompanyAtAll() throws IOException {
        when(database.connection()).thenReturn(Optional.of(
            new PostgresConnection("localhost", 5432, "invoicing", "public", "invoicing", "secret")));
        doAnswer(call -> Files.writeString(call.getArgument(1), "PGDMP shared")).when(tools).dump(any(), any());
        // As in the application: the scheduler backs up the shared data with no company set
        CompanyContext.setFallback("");

        BackupFile shared = backupService.backupShared();

        assertThat(shared.name()).startsWith("swift-invoicing-commun-");
        assertThat(FOLDER.resolve(shared.name())).exists();
        assertThat(backupService.isSharedBackupDue()).isFalse();
    }
}
