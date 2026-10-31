package com.swiftlabs.invoicing.audit;

import com.swiftlabs.invoicing.access.privilege.PrivilegeRepository;
import com.swiftlabs.invoicing.access.profile.Profile;
import com.swiftlabs.invoicing.access.profile.ProfileRepository;
import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;
import com.swiftlabs.invoicing.company.CompanyAccessService;
import com.swiftlabs.invoicing.company.CompanySchemas;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The activity journal: sign-ins, sign-outs, pages and actions are kept with who, where and when, and shown to
 * whoever may see them. Not transactional: the journal is written in its own transactions, as in production.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditJournalTest {

    private static final String PASSWORD = "Secret123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private PrivilegeRepository privilegeRepository;

    @Autowired
    private CompanyAccessService accessService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private AuditRecorder recorder;

    @Autowired
    private AuditService auditService;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private AuditDescriptions descriptions;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> users = new ArrayList<>();
    private final List<Long> profiles = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM public.audit_event WHERE username LIKE 'journal\\_%' ESCAPE '\\'");
        for (Long id : users) {
            jdbc.update("DELETE FROM public.app_user WHERE id = ?", id);
        }
        for (Long id : profiles) {
            jdbc.update("DELETE FROM public.profile_privilege WHERE profile_id = ?", id);
            jdbc.update("DELETE FROM public.profile WHERE id = ?", id);
        }
    }

    private User user(String username, Profile profile) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            User user = new User();
            user.setUsername(username);
            user.setEmail(username + "@example.test");
            user.changePassword(passwordEncoder.encode(PASSWORD), LocalDateTime.now());
            user = userRepository.save(user);
            users.add(user.getId());
            accessService.grant(user, CompanySchemas.FIRST_ID, profile);
            return user;
        });
    }

    private Profile administrator() {
        return profileRepository.findByName("Administrateur");
    }

    private Profile clientsOnly() {
        Profile saved = new TransactionTemplate(transactionManager).execute(status -> {
            Profile profile = new Profile();
            profile.setName("Clients seulement " + System.nanoTime());
            profile.getPrivileges().add(privilegeRepository.findByCode("VIEW_CLIENTS"));
            return profileRepository.save(profile);
        });
        profiles.add(saved.getId());
        return saved;
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/perform-login").session(session).with(csrf()).param("username", username).param("password", password)
            .with(request -> {
                request.setRemoteAddr("192.168.1.20");
                return request;
            }));
        return session;
    }

    private List<Map<String, Object>> journalOf(String username) {
        return jdbc.queryForList("SELECT * FROM public.audit_event WHERE username = ? ORDER BY id", username);
    }

    @Test
    void signingInViewingActingAndSigningOutAreKept() throws Exception {
        User user = user("journal_karim", administrator());
        MockHttpSession session = login("journal_karim", PASSWORD);
        mockMvc.perform(get("/clients").session(session).with(request -> {
            request.setRemoteAddr("192.168.1.20");
            return request;
        })).andExpect(status().isOk());
        mockMvc.perform(post("/clients/save").session(session).with(csrf()).param("type", "PARTICULIER").param("name", ""))
            .andExpect(status().isOk());
        mockMvc.perform(post("/perform-logout").session(session).with(csrf()));

        List<Map<String, Object>> journal = journalOf("journal_karim");
        assertThat(journal).extracting(row -> row.get("kind")).containsExactly("SIGN_IN", "VIEW", "ACTION", "SIGN_OUT");
        assertThat(journal).allSatisfy(row -> assertThat(row.get("user_id")).isEqualTo(user.getId()));

        Map<String, Object> signIn = journal.get(0);
        assertThat(signIn.get("ip")).isEqualTo("192.168.1.20");

        Map<String, Object> view = journal.get(1);
        assertThat(view.get("path")).isEqualTo("/clients");
        assertThat(view.get("status")).isEqualTo(200);
        assertThat(view.get("handler")).isEqualTo("ClientController#list");
        assertThat(view.get("company_id")).isEqualTo(CompanySchemas.FIRST_ID);
        assertThat(view.get("ip")).isEqualTo("192.168.1.20");

        Map<String, Object> action = journal.get(2);
        assertThat(action.get("method")).isEqualTo("POST");
        assertThat(action.get("handler")).isEqualTo("ClientController#save");
    }

    @Test
    void aWrongPasswordIsKeptWithTheAccountItWasFor() throws Exception {
        User user = user("journal_samia", administrator());
        login("journal_samia", "wrong-password");

        assertThat(journalOf("journal_samia")).singleElement().satisfies(row -> {
            assertThat(row.get("kind")).isEqualTo("SIGN_IN_FAILED");
            assertThat(row.get("user_id")).isEqualTo(user.getId());
            assertThat(row.get("ip")).isEqualTo("192.168.1.20");
        });
    }

    @Test
    void aSessionClosedWithoutSigningOutIsKept() throws Exception {
        user("journal_nadia", administrator());
        MockHttpSession session = login("journal_nadia", PASSWORD);
        SecurityContext context = (SecurityContext) session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context).isNotNull();

        events.publishEvent(new HttpSessionDestroyedEvent(session));

        assertThat(journalOf("journal_nadia")).extracting(row -> row.get("kind")).containsExactly("SIGN_IN", "SESSION_ENDED");
    }

    @Test
    void secretsInAddressesAreMasked() throws Exception {
        user("journal_omar", administrator());
        MockHttpSession session = login("journal_omar", PASSWORD);
        mockMvc.perform(get("/clients?token=abc123&search=x").session(session));

        assertThat(journalOf("journal_omar")).extracting(row -> row.get("path"))
            .contains("/clients?token=***&search=x")
            .noneMatch(path -> path != null && path.toString().contains("abc123"));
    }

    @Test
    void pagesShownToVisitorsAreNotKept() throws Exception {
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM public.audit_event WHERE path = '/login'", Long.class);
        mockMvc.perform(get("/login")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM public.audit_event WHERE path = '/login'", Long.class)).isEqualTo(before);
    }

    @Test
    void theJournalShowsTheCompanysActivityToWhoeverMaySeeIt() throws Exception {
        user("journal_admin", administrator());
        user("journal_clerk", clientsOnly());
        // Done in another company: not shown in this one
        recorder.record(AuditKind.ACTION, new AuditRecorder.Actor(null, "journal_elsewhere", 999_999L, "Autre SARL"),
            "POST", "/clients/save", 302, 5, "ClientController#save", "10.0.0.9", null);

        MockHttpSession clerk = login("journal_clerk", PASSWORD);
        mockMvc.perform(get("/settings/audit").session(clerk)).andExpect(status().isForbidden());
        mockMvc.perform(get("/clients").session(clerk)).andExpect(status().isOk());

        MockHttpSession admin = login("journal_admin", PASSWORD);
        mockMvc.perform(get("/settings/audit").session(admin))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Journal d&#39;activité")))
            .andExpect(content().string(containsString("journal_clerk")))
            .andExpect(content().string(containsString("AuditController#list")))
            .andExpect(content().string(not(containsString("journal_elsewhere"))));

        // The refused page is kept as refused
        assertThat(journalOf("journal_clerk")).filteredOn(row -> "/settings/audit".equals(row.get("path")))
            .singleElement().satisfies(row -> assertThat(row.get("status")).isEqualTo(403));

        // Filtered on a kind and a user
        mockMvc.perform(get("/settings/audit").session(admin).param("kind", "SIGN_IN").param("user", "clerk"))
            .andExpect(content().string(containsString("journal_clerk")))
            .andExpect(content().string(not(containsString("ClientController#list"))));

        // Every company: only for whoever manages them (the administrator here)
        mockMvc.perform(get("/settings/audit").session(admin).param("all", "true").param("user", "journal_"))
            .andExpect(content().string(containsString("journal_elsewhere")))
            .andExpect(content().string(containsString("Autre SARL")));
    }

    @Test
    void entriesReadAsSentencesNamingWhatTheyAreAbout() throws Exception {
        user("journal_lina", administrator());
        Client client = new TransactionTemplate(transactionManager).execute(status -> {
            Client created = new Client();
            created.setType(ClientType.PARTICULIER);
            created.setName("Client du journal");
            return clientRepository.save(created);
        });
        recorder.record(AuditKind.VIEW, new AuditRecorder.Actor(null, "journal_lina", CompanySchemas.FIRST_ID, "Société"),
            "GET", "/invoices/5/pdf", 200, 30, "InvoiceController#pdf", "10.0.0.2", new AuditSubjects.Subject("INVOICE", "FA-2026-0001"));

        MockHttpSession session = login("journal_lina", PASSWORD);
        mockMvc.perform(post("/clients/" + client.getId() + "/delete").session(session).with(csrf()));

        // Named although deleted: read before the deletion
        assertThat(journalOf("journal_lina")).filteredOn(row -> "ClientController#delete".equals(row.get("handler")))
            .singleElement().satisfies(row -> assertThat(row.get("subject")).isEqualTo("Client du journal"));
        mockMvc.perform(get("/settings/audit").session(session).param("user", "journal_lina"))
            .andExpect(content().string(containsString("Impression de la facture FA-2026-0001")))
            .andExpect(content().string(containsString("Suppression du client « Client du journal »")));
        // In the reader's language (a signed-in user reads the application in their own)
        AuditFilter printed = new AuditFilter();
        printed.setSearch("FA-2026-0001");
        printed.setUser("journal_lina");
        AuditEvent event = auditService.search(printed, CompanySchemas.FIRST_ID, PageRequest.of(0, 1)).getContent().get(0);
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        try {
            assertThat(descriptions.of(event)).isEqualTo("Printed invoice FA-2026-0001");
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
        // Searched by what it is about
        mockMvc.perform(get("/settings/audit").session(session).param("search", "FA-2026-0001"))
            .andExpect(content().string(containsString("Impression de la facture FA-2026-0001")));
    }

    @Test
    void oldEntriesAreDeleted() {
        recorder.record(AuditKind.SIGN_IN, new AuditRecorder.Actor(null, "journal_old", null, null), "10.0.0.1");
        jdbc.update("UPDATE public.audit_event SET occurred_at = ? WHERE username = 'journal_old'",
            Timestamp.valueOf(LocalDateTime.now().minusDays(400)));
        recorder.record(AuditKind.SIGN_IN, new AuditRecorder.Actor(null, "journal_recent", null, null), "10.0.0.1");

        auditService.deleteOlderThan(LocalDateTime.now().minusDays(365));

        assertThat(journalOf("journal_old")).isEmpty();
        assertThat(journalOf("journal_recent")).hasSize(1);
    }
}
