package com.swiftlabs.invoicing.access.user;

import com.swiftlabs.invoicing.access.profile.Profile;

import com.swiftlabs.invoicing.common.Language;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /** Temporary password (new account, admin reset): the user must choose a new one at next login. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    /** Base32 secret shared with the authenticator app; null until two-factor login is set up. */
    @Column(name = "totp_secret", length = 64)
    private String totpSecret;

    /** Language of the interface for this user: fr, en or ar (see Language). */
    @Column(name = "language", nullable = false, length = 5)
    private String language = Language.FR.getCode();

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled;

    /** Last 30-second step accepted, so a code cannot be used twice. */
    @Column(name = "totp_last_step")
    private Long totpLastStep;

    public User() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public LocalDateTime getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public boolean isLocked(LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Counts a failed login; locks the account once {@code maxAttempts} failures in a row are reached. */
    public void recordFailedLogin(int maxAttempts, Duration lockDuration, LocalDateTime now) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            failedLoginAttempts = 0;
        }
    }

    public void recordSuccessfulLogin(LocalDateTime now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    /** Sets a password chosen by the user: clears the forced change and any lock. */
    public void changePassword(String passwordHash, LocalDateTime now) {
        this.password = passwordHash;
        this.passwordChangedAt = now;
        this.mustChangePassword = false;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    /** Sets a temporary password that must be changed at next login. */
    public void assignTemporaryPassword(String passwordHash, LocalDateTime now) {
        changePassword(passwordHash, now);
        this.mustChangePassword = true;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = blankToNull(firstName);
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = blankToNull(lastName);
    }

    /** What the application calls the user: the first name, or the username when it is not filled in. */
    public String getDisplayName() {
        return firstName != null ? firstName : username;
    }

    /** "First Last", or the username when neither is filled in. */
    public String getFullName() {
        String full = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
        return full.isEmpty() ? username : full;
    }

    public Language getLanguage() {
        return Language.of(language);
    }

    public void setLanguage(Language language) {
        this.language = (language == null ? Language.FR : language).getCode();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public String getTotpSecret() {
        return totpSecret;
    }

    public boolean isTotpEnabled() {
        return totpEnabled;
    }

    public Long getTotpLastStep() {
        return totpLastStep;
    }

    /** Turns two-factor login on with a secret the user has just proved they set up (code of {@code step}). */
    public void enableTotp(String secret, long step) {
        this.totpSecret = secret;
        this.totpEnabled = true;
        this.totpLastStep = step;
    }

    public void disableTotp() {
        this.totpSecret = null;
        this.totpEnabled = false;
        this.totpLastStep = null;
    }

    public void acceptTotpStep(long step) {
        this.totpLastStep = step;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(username, user.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }
}
