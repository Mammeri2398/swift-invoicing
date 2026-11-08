package com.swiftlabs.invoicing.access.user;

import com.swiftlabs.invoicing.access.privilege.Privilege;
import com.swiftlabs.invoicing.access.profile.Profile;
import com.swiftlabs.invoicing.access.profile.ProfileRepository;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.Language;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Privileges;
import com.swiftlabs.invoicing.common.ResourceNotFoundException;
import com.swiftlabs.invoicing.company.CompanyAccess;
import com.swiftlabs.invoicing.company.CompanyAccessService;
import com.swiftlabs.invoicing.company.CompanyContext;
import com.swiftlabs.invoicing.licence.LicenceService;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * Username and password are not editable here. An administrator cannot disable their own account. Who does not
     * manage every company of the user changes only their access to their own companies: the account itself (name,
     * email, language, active) belongs to the administrators of all its companies.
     */
    @Transactional
    public User update(UserForm form, Long actingUserId) {
        User existing = requireVisible(form.getId(), actingUserId);
        Map<Long, Profile> accesses = plannedAccesses(existing.getId(), form, actingUserId);
        if (!fullyManages(actingUserId, existing.getId())) {
            keepAdministrators(existing, accesses, existing.isEnabled());
            applyAccesses(existing, accesses);
            return existing;
        }
        if (!form.getEmail().equals(existing.getEmail()) && userRepository.existsByEmail(form.getEmail())) {
            throw BusinessException.onField("email", MessageProperties.USER_ERROR_EMAIL_USED);
        }
        if (!form.isEnabled() && Objects.equals(existing.getId(), actingUserId)) {
            throw BusinessException.onField("enabled", MessageProperties.USER_ERROR_DISABLE_SELF);
        }
        if (form.isEnabled() && !existing.isEnabled()) {
            licenceService.checkUserLimit(userRepository.countByEnabledTrue() + 1);
        }
        keepAdministrators(existing, accesses, form.isEnabled());
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

    /**
     * The user's own personal information (Mon compte). The username and the profile are not editable here. A new
     * email needs the current password: reset links go there, so a session left open would be enough to take the
     * account over.
     */
    @Transactional
    public User updateOwnDetails(Long userId, String firstName, String lastName, String email, String currentPassword) {
        User user = require(userId);
        if (!email.equalsIgnoreCase(user.getEmail())
            && (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword()))) {
            throw BusinessException.onField("currentPassword", MessageProperties.USER_ERROR_WRONG_CURRENT_PASSWORD);
        }
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
    public String resetToTemporaryPassword(Long userId, Long actingUserId) {
        requireFullyManaged(actingUserId, userId);
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
        requireFullyManaged(actingUserId, id);
        User user = require(id);
        keepAdministrators(user, Map.of(), false);
        userRepository.delete(user);
    }

    /**
     * Refuses a change that would leave nobody to manage the users of a company (MANAGE_USERS) or the profiles
     * (MANAGE_PROFILES, shared by all companies): disabling or deleting the last one who can, or taking the right
     * away from them. Only a database fix could give it back. {@code changes}: the profile per company after the
     * change (null: access removed); {@code staysEnabled}: whether the account is still active afterwards.
     */
    private void keepAdministrators(User user, Map<Long, Profile> changes, boolean staysEnabled) {
        if (!user.isEnabled()) {
            return;
        }
        List<CompanyAccess> current = accessService.accessesOf(user.getId());
        Map<Long, Profile> after = new HashMap<>();
        current.forEach(access -> after.put(access.getCompany().getId(), access.getProfile()));
        after.putAll(changes);
        for (CompanyAccess access : current) {
            Long companyId = access.getCompany().getId();
            if (holds(access.getProfile(), Privileges.MANAGE_USERS)
                && !(staysEnabled && holds(after.get(companyId), Privileges.MANAGE_USERS))
                && !accessService.hasOtherHolder(Privileges.MANAGE_USERS, companyId, user.getId())) {
                throw new BusinessException(MessageProperties.USER_ERROR_LAST_USER_MANAGER, access.getCompany().getName());
            }
        }
        boolean managedProfiles = current.stream().anyMatch(access -> holds(access.getProfile(), Privileges.MANAGE_PROFILES));
        boolean stillManagesProfiles = staysEnabled && after.values().stream().anyMatch(profile -> holds(profile, Privileges.MANAGE_PROFILES));
        if (managedProfiles && !stillManagesProfiles && !accessService.hasOtherHolder(Privileges.MANAGE_PROFILES, null, user.getId())) {
            throw new BusinessException(MessageProperties.USER_ERROR_LAST_PROFILE_MANAGER);
        }
    }

    private static boolean holds(Profile profile, String privilegeCode) {
        return profile != null && profile.getPrivileges().stream().anyMatch(p -> privilegeCode.equals(p.getCode()));
    }

    /** The privilege codes of the administrator's profile in the company (none without access). */
    private Set<String> privilegesIn(Long userId, Long companyId) {
        return accessService.profileIn(userId, companyId)
            .map(profile -> profile.getPrivileges().stream().map(Privilege::getCode).collect(Collectors.toSet()))
            .orElse(Set.of());
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
            Profile profile = row.getProfileId() == null ? null : requireProfile(row.getProfileId(), i);
            // A new profile given in a company holds no privilege the administrator lacks there: otherwise managing
            // users would be a way to give oneself (through another account) any right, Administrateur included
            if (profile != null && actingUserId != null && !isCurrentProfile(userId, row.getCompanyId(), profile)) {
                Set<String> own = privilegesIn(actingUserId, row.getCompanyId());
                if (!profile.getPrivileges().stream().allMatch(privilege -> own.contains(privilege.getCode()))) {
                    throw BusinessException.onField("accesses[" + i + "].profileId", MessageProperties.USER_ERROR_PROFILE_ABOVE_OWN);
                }
            }
            changes.put(row.getCompanyId(), profile);
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

    /** The user already has this profile in the company: keeping it gives nothing new. */
    private boolean isCurrentProfile(Long userId, Long companyId, Profile profile) {
        return userId != null && accessService.profileIn(userId, companyId).map(Profile::getId)
            .filter(id -> id.equals(profile.getId())).isPresent();
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

    /**
     * The administrator manages every company the user works in (or the user works in none): the account is theirs
     * to change, reset or delete. Otherwise taking over the account would open companies the administrator cannot
     * manage. {@code actingUserId} null: the application itself.
     */
    @Transactional(readOnly = true)
    public boolean fullyManages(Long actingUserId, Long userId) {
        return actingUserId == null || accessService.companiesManagedBy(actingUserId).containsAll(accessService.companyIdsOf(userId));
    }

    /** Refuses an action on the account of a user who works in a company the administrator does not manage. */
    @Transactional(readOnly = true)
    public void requireFullyManaged(Long actingUserId, Long userId) {
        if (!fullyManages(actingUserId, userId)) {
            throw new BusinessException(MessageProperties.USER_ERROR_OTHER_COMPANIES);
        }
    }

    /** The users an administrator sees: those of the companies they manage, and those of no company yet. */
    @Transactional(readOnly = true)
    public List<User> findVisibleTo(Long actingUserId) {
        Set<Long> managed = accessService.companiesManagedBy(actingUserId);
        return userRepository.findAll().stream().filter(user -> isVisible(managed, user.getId())).toList();
    }

    /**
     * The account, when the administrator may see it (see findVisibleTo); otherwise "not found", as for an id that
     * does not exist: the users of the companies they do not manage are not theirs to read.
     */
    @Transactional(readOnly = true)
    public User requireVisible(Long userId, Long actingUserId) {
        User user = require(userId);
        if (actingUserId != null && !isVisible(accessService.companiesManagedBy(actingUserId), userId)) {
            throw new ResourceNotFoundException(MessageProperties.USER_ERROR_NOT_FOUND);
        }
        return user;
    }

    private boolean isVisible(Set<Long> managed, Long userId) {
        Set<Long> companies = accessService.companyIdsOf(userId);
        return companies.isEmpty() || companies.stream().anyMatch(managed::contains);
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
