package com.swiftlabs.invoicing.licence;

import com.swiftlabs.invoicing.access.profile.ProfileRepository;
import com.swiftlabs.invoicing.access.user.UserForm;
import com.swiftlabs.invoicing.access.user.UserService;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.issuer.IssuerService;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** An on-premise install with licensing on, checked against a test publisher key (src/test/resources/licence). */
@SpringBootTest(properties = {
    "app.licence.required=true",
    "app.licence.public-key=MCowBQYDK2VwAyEARonTCCmuO8s8V0FoIiFExp1NlZyubvPcR5QHefxM5aQ=",
})
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = {"VIEW_ISSUER", "EDIT_ISSUER", "VIEW_CLIENTS", "CREATE_CLIENT", "MANAGE_USERS"})
class LicenceTest {

    private static final String NIF = "000016001234567";

    @Autowired
    private LicenceService licenceService;

    @Autowired
    private IssuerService issuerService;

    @Autowired
    private UserService userService;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MockMvc mockMvc;

    private PrivateKey publisher;

    @BeforeEach
    void install() throws Exception {
        publisher = LicenceCodec.privateKey(new ClassPathResource("licence/test-private.key").getContentAsString(StandardCharsets.UTF_8));
        issuerService.getIssuer().setNif(NIF);
        entityManager.flush();
        jdbc.update("DELETE FROM licence");
        entityManager.clear();
    }

    private String key(String nif, int users, LocalDate expires) {
        return LicenceCodec.sign(new Licence("LIC-TEST-0001", "Atlas Solutions", nif, users, LocalDate.now().minusYears(1), expires),
            publisher);
    }

    /** Changes the licence row behind JPA's back: flushed first, and the cached entity forgotten after. */
    private void sql(String update, Object... args) {
        licenceService.status();
        entityManager.flush();
        jdbc.update(update, args);
        entityManager.clear();
    }

    /** Stores a key as if activated earlier (activation itself refuses expired keys). */
    private void stored(String key) {
        sql("UPDATE licence SET licence_key = ?", key);
    }

    private void trialStartedDaysAgo(int days) {
        Timestamp start = Timestamp.valueOf(LocalDateTime.now().minusDays(days));
        sql("UPDATE licence SET trial_started_at = ?, last_seen_at = ?", start, start);
    }

    @Test
    void aFreshInstallIsInTrialForThirtyDays() {
        LicenceStatus status = licenceService.status();
        assertThat(status.state()).isEqualTo(LicenceStatus.State.TRIAL);
        assertThat(status.daysLeft()).isEqualTo(30);
        assertThat(status.isReadOnly()).isFalse();
        assertThat(licenceService.maxUsers()).isEqualTo(3);
    }

    @Test
    void afterTheTrialEverythingTurnsReadOnly() throws Exception {
        trialStartedDaysAgo(31);
        assertThat(licenceService.status().state()).isEqualTo(LicenceStatus.State.TRIAL_OVER);

        mockMvc.perform(post("/clients/save").with(csrf()).param("type", "PARTICULIER").param("name", "Nouveau"))
            .andExpect(redirectedUrl("/settings/licence?readonly"));
        mockMvc.perform(get("/clients")).andExpect(status().isOk());
        mockMvc.perform(get("/settings/licence").param("readonly", ""))
            .andExpect(content().string(containsString("l&#39;application est en lecture seule")))
            .andExpect(content().string(containsString("La période d&#39;essai est terminée")));
    }

    @Test
    void aValidKeyIsActivatedAndLiftsTheReadOnlyMode() throws Exception {
        trialStartedDaysAgo(31);
        mockMvc.perform(post("/settings/licence").with(csrf()).param("key", key(NIF, 5, LocalDate.now().plusYears(1))))
            .andExpect(redirectedUrl("/settings/licence"))
            .andExpect(flash().attribute("success", containsString("Licence LIC-TEST-0001 activée pour Atlas Solutions : 5 utilisateur(s)")));
        LicenceStatus status = licenceService.status();
        assertThat(status.state()).isEqualTo(LicenceStatus.State.VALID);
        assertThat(licenceService.maxUsers()).isEqualTo(5);
        mockMvc.perform(post("/clients/save").with(csrf()).param("type", "PARTICULIER").param("name", "Nouveau"))
            .andExpect(redirectedUrl("/clients"));
    }

    @Test
    void keysForAnotherCompanyOrAlreadyExpiredAreRefused() throws Exception {
        mockMvc.perform(post("/settings/licence").with(csrf()).param("key", key("999999999999999", 3, LocalDate.now().plusYears(1))))
            .andExpect(flash().attribute("error", containsString("établie pour le NIF 999999999999999")));
        assertThatThrownBy(() -> licenceService.activate(key(NIF, 3, LocalDate.now().minusDays(1))))
            .isInstanceOf(InvalidLicenceException.class).hasMessageContaining("a expiré");
        assertThatThrownBy(() -> licenceService.activate("SWIFT-pas-une-cle")).isInstanceOf(InvalidLicenceException.class);

        // A licence stops matching if the issuer's NIF is changed afterwards
        licenceService.activate(key(NIF, 3, LocalDate.now().plusYears(1)));
        issuerService.getIssuer().setNif("123456789012345");
        assertThat(licenceService.status().state()).isEqualTo(LicenceStatus.State.INVALID);
        assertThat(licenceService.status().isReadOnly()).isTrue();
    }

    @Test
    void theEndOfALicenceIsAnnouncedThenGivenAGracePeriod() {
        stored(key(NIF, 3, LocalDate.now().plusDays(10)));
        assertThat(licenceService.status().state()).isEqualTo(LicenceStatus.State.EXPIRING);
        assertThat(licenceService.status().message()).contains("expire dans 10 jours");

        stored(key(NIF, 3, LocalDate.now().minusDays(5)));
        assertThat(licenceService.status().state()).isEqualTo(LicenceStatus.State.GRACE);
        assertThat(licenceService.status().isReadOnly()).isFalse();
        assertThat(licenceService.status().message()).contains("lecture seule dans 10 jours");

        stored(key(NIF, 3, LocalDate.now().minusDays(16)));
        assertThat(licenceService.status().state()).isEqualTo(LicenceStatus.State.EXPIRED);
        assertThat(licenceService.status().isReadOnly()).isTrue();
    }

    @Test
    void movingThePcClockBackDoesNotExtendTheLicence() {
        stored(key(NIF, 3, LocalDate.now().plusDays(5)));
        // The application already ran 25 days from now: the clock was moved back since
        sql("UPDATE licence SET last_seen_at = ?", Timestamp.valueOf(LocalDateTime.now().plusDays(25)));
        assertThat(licenceService.status().state()).isEqualTo(LicenceStatus.State.EXPIRED);
    }

    @Test
    void theNumberOfActiveUsersIsLimited() {
        long active = jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE enabled = TRUE", Long.class);
        licenceService.activate(key(NIF, (int) active + 1, LocalDate.now().plusYears(1)));
        userService.create(userForm("collegue1"));
        assertThatThrownBy(() -> userService.create(userForm("collegue2")))
            .isInstanceOf(BusinessException.class).hasMessageContaining("permet " + (active + 1) + " utilisateur(s) actif(s)");
    }

    private UserForm userForm(String username) {
        UserForm form = new UserForm();
        form.setUsername(username);
        form.setEmail(username + "@atlas.test");
        form.setProfileId(profileRepository.findByName("Administrateur").getId());
        return form;
    }
}
