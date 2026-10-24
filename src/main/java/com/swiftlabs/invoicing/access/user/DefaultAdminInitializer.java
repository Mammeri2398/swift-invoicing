package com.swiftlabs.invoicing.access.user;

import com.swiftlabs.invoicing.access.profile.Profile;
import com.swiftlabs.invoicing.access.profile.ProfileRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the default admin/admin account on first start. Never touches an existing account. */
@Component
public class DefaultAdminInitializer implements CommandLineRunner {

    private static final String ADMIN_PROFILE = "Administrateur";

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;

    public DefaultAdminInitializer(UserRepository userRepository, ProfileRepository profileRepository,
                                   PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByUsername("admin").isPresent()) {
            return;
        }
        Profile adminProfile = profileRepository.findByName(ADMIN_PROFILE);
        if (adminProfile == null) {
            adminProfile = new Profile();
            adminProfile.setName(ADMIN_PROFILE);
            adminProfile.setDescription("Accès complet à toutes les fonctionnalités");
            adminProfile = profileRepository.save(adminProfile);
        }
        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail("admin@swiftinvoicing.local");
        admin.setProfile(adminProfile);
        admin.setPassword(passwordEncoder.encode("admin"));
        userRepository.save(admin);
    }
}
