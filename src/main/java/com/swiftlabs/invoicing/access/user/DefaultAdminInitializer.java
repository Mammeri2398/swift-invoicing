package com.swiftlabs.invoicing.access.user;

import com.swiftlabs.invoicing.access.profile.Profile;
import com.swiftlabs.invoicing.access.profile.ProfileRepository;
import com.swiftlabs.invoicing.company.CompanyAccessService;
import com.swiftlabs.invoicing.company.CompanySchemas;
import com.swiftlabs.invoicing.config.SecurityProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Creates the "admin" account on first start, with the password from
 * {@code app.security.initial-admin-password} or a random one written once to the log.
 * Either way it must be changed at the first login. An existing account is never touched.
 * Not used on-premise (app.setup.wizard): the setup wizard creates the first account.
 */
@Component
public class DefaultAdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultAdminInitializer.class);
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PROFILE = "Administrateur";

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SecurityProperties properties;
    private final CompanyAccessService accessService;
    private final Clock clock;
    private final boolean setupWizard;

    public DefaultAdminInitializer(UserRepository userRepository, ProfileRepository profileRepository,
                                   PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                                   SecurityProperties properties, CompanyAccessService accessService, Clock clock,
                                   @Value("${app.setup.wizard:false}") boolean setupWizard) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.properties = properties;
        this.accessService = accessService;
        this.clock = clock;
        this.setupWizard = setupWizard;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (setupWizard || userRepository.findByUsername(ADMIN_USERNAME).isPresent()) {
            return;
        }
        Profile adminProfile = profileRepository.findByName(ADMIN_PROFILE);
        if (adminProfile == null) {
            throw new IllegalStateException("Profile '" + ADMIN_PROFILE + "' is missing: it is created by the V2 migration.");
        }

        String configured = properties.initialAdminPassword();
        boolean generated = configured == null || configured.isBlank();
        String password = generated ? passwordPolicy.generateTemporary() : configured;

        User admin = new User();
        admin.setUsername(ADMIN_USERNAME);
        admin.setEmail("admin@swiftinvoicing.local");
        admin.assignTemporaryPassword(passwordEncoder.encode(password), LocalDateTime.now(clock));
        // Administrator of the first company (the only one of a new installation)
        accessService.grant(userRepository.save(admin), CompanySchemas.FIRST_ID, adminProfile);

        if (generated) {
            log.warn("Created the '{}' account with the temporary password: {} "
                + "(shown only once; it must be changed at the first login)", ADMIN_USERNAME, password);
        } else {
            log.info("Created the '{}' account with the configured initial password (to change at the first login)", ADMIN_USERNAME);
        }
    }
}
