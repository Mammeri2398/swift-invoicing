package com.swiftlabs.invoicing.access.user;

import com.swiftlabs.invoicing.access.profile.Profile;
import com.swiftlabs.invoicing.access.profile.ProfileRepository;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.Language;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.ResourceNotFoundException;
import com.swiftlabs.invoicing.company.CompanyAccessService;
import com.swiftlabs.invoicing.company.CompanyContext;
import com.swiftlabs.invoicing.licence.LicenceService;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
    private final LicenceService licenceService;
    private final CompanyAccessService accessService;
    private final Clock clock;

    public UserService(UserRepository userRepository, ProfileRepository profileRepository,
                       PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy, LicenceService licenceService,
                       CompanyAccessService accessService, Clock clock) {
        this.licenceService = licenceService;
        this.accessService = accessService;
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

    /**
     * Creates an account with a random temporary password that must be changed at first login, with the companies
     * and profiles of the form. {@code actingUserId}: the administrator, who sets only the companies they manage;
     * null for the application itself (every company).
     */
    @Transactional
    public NewAccount create(UserForm form, Long actingUserId) {
        Map<Long, Profile> accesses = plannedAccesses(null, form, actingUserId);
        if (userRepository.existsByUsername(form.getUsername())) {
            throw BusinessException.onField("username", MessageProperties.USER_ERROR_USERNAME_USED);
        }
        if (userRepository.existsByEmail(form.getEmail())) {
            throw BusinessException.onField("email", MessageProperties.USER_ERROR_EMAIL_USED);
        }
        // The licence limits the number of active accounts
        licenceService.checkUserLimit(userRepository.countByEnabledTrue() + 1);
        String temporaryPassword = passwordPolicy.generateTemporary();
        User user = new User();
        user.setUsername(form.getUsername());
        user.setFirstName(form.getFirstName());
        user.setLastName(form.getLastName());
        user.setEmail(form.getEmail());
        user.setLanguage(form.getLanguage());
        user.setEnabled(true);
        user.assignTemporaryPassword(passwordEncoder.encode(temporaryPassword), now());
        User saved = userRepository.save(user);
        applyAccesses(saved, accesses);
        return new NewAccount(saved, temporaryPassword);
    }

    /** Username and password are not editable here. An administrator cannot disable their own account. */
    @Transactional
    public User update(UserForm form, Long actingUserId) {
        User existing = require(form.getId());
        Map<Long, Profile> accesses = plannedAccesses(existing.getId(), form, actingUserId);
        if (!form.getEmail().equals(existing.getEmail()) && userRepository.existsByEmail(form.getEmail())) {
            throw BusinessException.onField("email", MessageProperties.USER_ERROR_EMAIL_USED);
        }
        if (!form.isEnabled() && Objects.equals(existing.getId(), actingUserId)) {
            throw BusinessException.onField("enabled", MessageProperties.USER_ERROR_DISABLE_SELF);
        }
        if (form.isEnabled() && !existing.isEnabled()) {
            licenceService.checkUserLimit(userRepository.countByEnabledTrue() + 1);
        }
        existing.setFirstName(form.getFirstName());
        existing.setLastName(form.getLastName());
        existing.setEmail(form.getEmail());
        existing.setEnabled(form.isEnabled());
        applyAccesses(existing, accesses);
        if (form.getLanguage() != null) {
            existing.setLanguage(form.getLanguage());
        }
        return userRepository.save(existing);
    }

    /** The user's own personal information (Mon compte). The username and the profile are not editable here. */
    @Transactional
    public User updateOwnDetails(Long userId, String firstName, String lastName, String email) {
        User user = require(userId);
        if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw BusinessException.onField("email", MessageProperties.USER_ERROR_EMAIL_USED);
        }
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        return user;
    }

    /** The language of the interface chosen by the user (Mon compte). */
    @Transactional
    public User updateOwnLanguage(Long userId, Language language) {
        User user = require(userId);
        user.setLanguage(language);
        return user;
    }

    /** Self-service change: the current password is required. */
    @Transactional
    public void changeOwnPassword(Long userId, String currentPassword, String newPassword) {
        User user = require(userId);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw BusinessException.onField("currentPassword", MessageProperties.USER_ERROR_WRONG_CURRENT_PASSWORD);
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw BusinessException.onField("newPassword", MessageProperties.USER_ERROR_SAME_PASSWORD);
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
            throw new BusinessException(MessageProperties.USER_ERROR_DELETE_SELF);
        }
        userRepository.delete(require(id));
    }

    private void setPassword(User user, String newPassword) {
        passwordPolicy.validate(newPassword, user.getUsername());
        user.changePassword(passwordEncoder.encode(newPassword), now());
    }

    /**
     * The profile chosen in each company of the form, among those the administrator manages (rows of other
     * companies are ignored: they cannot be changed from here). The administrator's own access to the company they
     * work in stays as it is, so that they cannot lock themselves out. The user must keep at least one company.
     */
    private Map<Long, Profile> plannedAccesses(Long userId, UserForm form, Long actingUserId) {
        Set<Long> managed = actingUserId != null ? accessService.companiesManagedBy(actingUserId) : null;
        Long current = CompanyContext.currentCompanyId();
        Map<Long, Profile> changes = new LinkedHashMap<>();
        for (int i = 0; i < form.getAccesses().size(); i++) {
            UserForm.Access row = form.getAccesses().get(i);
            if (row.getCompanyId() == null || (managed != null && !managed.contains(row.getCompanyId()))
                || (userId != null && Objects.equals(userId, actingUserId) && Objects.equals(row.getCompanyId(), current))) {
                continue;
            }
            changes.put(row.getCompanyId(), row.getProfileId() == null ? null : requireProfile(row.getProfileId(), i));
        }
        Set<Long> companies = new HashSet<>();
        if (userId != null) {
            accessService.accessesOf(userId).forEach(access -> companies.add(access.getCompany().getId()));
        }
        changes.forEach((companyId, profile) -> {
            if (profile == null) {
                companies.remove(companyId);
            } else {
                companies.add(companyId);
            }
        });
        if (companies.isEmpty()) {
            throw BusinessException.onField("accesses", MessageProperties.USER_ERROR_NO_COMPANY);
        }
        return changes;
    }

    private void applyAccesses(User user, Map<Long, Profile> changes) {
        changes.forEach((companyId, profile) -> {
            if (profile == null) {
                accessService.revoke(user.getId(), companyId);
            } else {
                accessService.grant(user, companyId, profile);
            }
        });
    }

    private User require(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(MessageProperties.USER_ERROR_NOT_FOUND));
    }

    private Profile requireProfile(Long profileId, int row) {
        return profileRepository.findById(profileId)
            .orElseThrow(() -> BusinessException.onField("accesses[" + row + "].profileId", MessageProperties.USER_ERROR_PROFILE_NOT_FOUND));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
