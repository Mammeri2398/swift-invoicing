package com.swiftlabs.invoicing.security;

import com.swiftlabs.invoicing.security.domain.User;
import com.swiftlabs.invoicing.security.repository.ProfileRepository;
import com.swiftlabs.invoicing.security.repository.UserRepository;
import com.swiftlabs.invoicing.security.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, ProfileRepository profileRepository,
                           UserService userService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        com.swiftlabs.invoicing.security.domain.Profile adminProfile = profileRepository.findByName("Administrateur");
        if (adminProfile == null) {
            com.swiftlabs.invoicing.security.domain.Profile newProfile = new com.swiftlabs.invoicing.security.domain.Profile();
            newProfile.setName("Administrateur");
            newProfile.setDescription("Accès complet à toutes les fonctionnalités");
            adminProfile = profileRepository.save(newProfile);
        }

        User admin = userRepository.findByUsername("admin").orElse(null);
        if (admin == null) {
            admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@swiftinvoicing.local");
            admin.setProfile(adminProfile);
        }
        admin.setPassword(passwordEncoder.encode("admin"));
        userRepository.save(admin);
    }
}
