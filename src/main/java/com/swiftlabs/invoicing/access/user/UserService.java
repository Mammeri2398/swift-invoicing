package com.swiftlabs.invoicing.access.user;

import com.swiftlabs.invoicing.access.profile.Profile;
import com.swiftlabs.invoicing.access.profile.ProfileRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    public UserService(UserRepository userRepository, ProfileRepository profileRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${security.password.min-length:8}") int minLength) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = new PasswordPolicy(minLength);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Transactional
    public User create(UserForm form, String rawPassword) {
        validatePassword(rawPassword);
        Profile profile = requireProfile(form.getProfileId());
        if (userRepository.existsByUsername(form.getUsername())) {
            throw new IllegalArgumentException("Ce nom d'utilisateur existe déjà.");
        }
        if (userRepository.existsByEmail(form.getEmail())) {
            throw new IllegalArgumentException("Cet email est déjà utilisé.");
        }
        User user = new User();
        user.setUsername(form.getUsername());
        user.setEmail(form.getEmail());
        user.setProfile(profile);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setEnabled(true);
        return userRepository.save(user);
    }

    /** Username and password are not editable here. */
    @Transactional
    public User update(UserForm form) {
        User existing = userRepository.findById(form.getId())
            .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
        Profile profile = requireProfile(form.getProfileId());
        if (!form.getEmail().equals(existing.getEmail()) && userRepository.existsByEmail(form.getEmail())) {
            throw new IllegalArgumentException("Cet email est déjà utilisé.");
        }
        existing.setEmail(form.getEmail());
        existing.setEnabled(form.isEnabled());
        existing.setProfile(profile);
        return userRepository.save(existing);
    }

    @Transactional
    public void changePassword(Long userId, String rawPassword) {
        validatePassword(rawPassword);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
        user.setPassword(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
    }

    @Transactional
    public void delete(Long id) {
        userRepository.deleteById(id);
    }

    private Profile requireProfile(Long profileId) {
        if (profileId == null) {
            throw new IllegalArgumentException("Un profil doit être attribué à l'utilisateur.");
        }
        return profileRepository.findById(profileId)
            .orElseThrow(() -> new IllegalArgumentException("Profil introuvable."));
    }

    private void validatePassword(String password) {
        if (!passwordPolicy.isValid(password)) {
            throw new IllegalArgumentException(passwordPolicy.getErrorMessage());
        }
    }
}
