package com.swiftlabs.invoicing.access.user;

import com.swiftlabs.invoicing.access.profile.Profile;
import com.swiftlabs.invoicing.access.profile.ProfileRepository;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.ResourceNotFoundException;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class UserService {

    /** A created account and its temporary password, to show once to the administrator. */
    public record NewAccount(User user, String temporaryPassword) {
    }

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final Clock clock;

    public UserService(UserRepository userRepository, ProfileRepository profileRepository,
                       PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy, Clock clock) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.clock = clock;
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    /** Creates an account with a random temporary password that must be changed at first login. */
    @Transactional
    public NewAccount create(UserForm form) {
        Profile profile = requireProfile(form.getProfileId());
        if (userRepository.existsByUsername(form.getUsername())) {
            throw new BusinessException("username", "Ce nom d'utilisateur existe déjà.");
        }
        if (userRepository.existsByEmail(form.getEmail())) {
            throw new BusinessException("email", "Cet email est déjà utilisé.");
        }
        String temporaryPassword = passwordPolicy.generateTemporary();
        User user = new User();
        user.setUsername(form.getUsername());
        user.setFirstName(form.getFirstName());
        user.setLastName(form.getLastName());
        user.setEmail(form.getEmail());
        user.setProfile(profile);
        user.setEnabled(true);
        user.assignTemporaryPassword(passwordEncoder.encode(temporaryPassword), now());
        return new NewAccount(userRepository.save(user), temporaryPassword);
    }

    /** Username and password are not editable here. An administrator cannot disable their own account. */
    @Transactional
    public User update(UserForm form, Long actingUserId) {
        User existing = require(form.getId());
        Profile profile = requireProfile(form.getProfileId());
        if (!form.getEmail().equals(existing.getEmail()) && userRepository.existsByEmail(form.getEmail())) {
            throw new BusinessException("email", "Cet email est déjà utilisé.");
        }
        if (!form.isEnabled() && Objects.equals(existing.getId(), actingUserId)) {
            throw new BusinessException("enabled", "Vous ne pouvez pas désactiver votre propre compte.");
        }
        existing.setFirstName(form.getFirstName());
        existing.setLastName(form.getLastName());
        existing.setEmail(form.getEmail());
        existing.setEnabled(form.isEnabled());
        existing.setProfile(profile);
        return userRepository.save(existing);
    }

    /** The user's own personal information (Mon compte). The username and the profile are not editable here. */
    @Transactional
    public User updateOwnDetails(Long userId, String firstName, String lastName, String email) {
        User user = require(userId);
        if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new BusinessException("email", "Cet email est déjà utilisé.");
        }
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        return user;
    }

    /** Self-service change: the current password is required. */
    @Transactional
    public void changeOwnPassword(Long userId, String currentPassword, String newPassword) {
        User user = require(userId);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessException("currentPassword", "Le mot de passe actuel est incorrect.");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BusinessException("newPassword", "Le nouveau mot de passe doit être différent de l'actuel.");
        }
        setPassword(user, newPassword);
    }

    /** Sets a password chosen by the user (password reset link). */
    @Transactional
    public void changePassword(Long userId, String newPassword) {
        setPassword(require(userId), newPassword);
    }

    /** Administrator action: new random temporary password, to change at next login. Unlocks the account. */
    @Transactional
    public String resetToTemporaryPassword(Long userId) {
        User user = require(userId);
        String temporaryPassword = passwordPolicy.generateTemporary();
        user.assignTemporaryPassword(passwordEncoder.encode(temporaryPassword), now());
        return temporaryPassword;
    }

    @Transactional
    public void delete(Long id, Long actingUserId) {
        if (Objects.equals(id, actingUserId)) {
            throw new BusinessException("Vous ne pouvez pas supprimer votre propre compte.");
        }
        userRepository.delete(require(id));
    }

    private void setPassword(User user, String newPassword) {
        passwordPolicy.validate(newPassword, user.getUsername());
        user.changePassword(passwordEncoder.encode(newPassword), now());
    }

    private User require(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable."));
    }

    private Profile requireProfile(Long profileId) {
        if (profileId == null) {
            throw new BusinessException("profileId", "Un profil doit être attribué à l'utilisateur.");
        }
        return profileRepository.findById(profileId)
            .orElseThrow(() -> new BusinessException("profileId", "Profil introuvable."));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
