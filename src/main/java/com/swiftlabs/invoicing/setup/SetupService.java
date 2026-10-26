package com.swiftlabs.invoicing.setup;

import com.swiftlabs.invoicing.access.profile.Profile;
import com.swiftlabs.invoicing.access.profile.ProfileRepository;
import com.swiftlabs.invoicing.access.user.PasswordPolicy;
import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.Language;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.issuer.IssuerService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * First-run setup of an on-premise install (app.setup.wizard): while no user exists, the
 * application only shows the setup page, which creates the administrator and fills the company
 * profile. Afterwards the page is closed for good.
 */
@Service
public class SetupService {

    private static final Logger log = LoggerFactory.getLogger(SetupService.class);
    private static final String ADMIN_PROFILE = "Administrateur";

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final IssuerService issuerService;
    private final Clock clock;
    private final boolean wizard;
    /** Once a user exists it stays so: no query per request after that. */
    private volatile boolean done;

    public SetupService(UserRepository userRepository, ProfileRepository profileRepository, PasswordEncoder passwordEncoder,
                        PasswordPolicy passwordPolicy, IssuerService issuerService, Clock clock,
                        @Value("${app.setup.wizard:false}") boolean wizard) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.issuerService = issuerService;
        this.clock = clock;
        this.wizard = wizard;
    }

    public boolean isSetupNeeded() {
        if (!wizard || done) {
            return false;
        }
        done = userRepository.count() > 0;
        return !done;
    }

    /**
     * Creates the administrator and saves the company profile, in one transaction. The administrator
     * works in the language the setup page was shown in.
     */
    @Transactional
    public synchronized User complete(SetupForm form, Language language) {
        if (!isSetupNeeded()) {
            throw new BusinessException(MessageProperties.SETUP_ERROR_ALREADY_DONE);
        }
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw BusinessException.onField("confirmPassword", MessageProperties.SETUP_ERROR_PASSWORD_MISMATCH);
        }
        try {
            passwordPolicy.validate(form.getPassword(), form.getAdminUsername());
        } catch (BusinessException ex) {
            throw BusinessException.onField("password", ex.getCode(), ex.getArgs());
        }
        // Same checks as the Paramètres page (identifiers required by legal status)
        issuerService.save(form.toIssuerForm());

        Profile profile = profileRepository.findByName(ADMIN_PROFILE);
        if (profile == null) {
            throw new IllegalStateException("Profile '" + ADMIN_PROFILE + "' is missing: it is created by the V2 migration.");
        }
        User admin = new User();
        admin.setUsername(form.getAdminUsername().trim());
        admin.setFirstName(form.getAdminFirstName());
        admin.setEmail(form.getAdminEmail().trim());
        admin.setProfile(profile);
        admin.setLanguage(language);
        admin.changePassword(passwordEncoder.encode(form.getPassword()), LocalDateTime.now(clock));
        User saved = userRepository.save(admin);
        done = true;
        log.info("Setup completed: administrator '{}' created", saved.getUsername());
        return saved;
    }
}
