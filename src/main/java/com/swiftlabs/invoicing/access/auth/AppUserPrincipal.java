package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.access.privilege.Privilege;
import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.common.Language;
import com.swiftlabs.invoicing.company.Company;
import com.swiftlabs.invoicing.company.CompanyAccess;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The authenticated user stored in the session: an immutable snapshot taken at login,
 * never the JPA entity (which is not serializable and would go stale). Privilege changes
 * apply at the next login.
 *
 * It carries the company the user works in and the privileges of their profile in that company; switching company
 * replaces it ({@link #withCompany}). No company yet (several to choose from): no privileges at all.
 */
public final class AppUserPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 2L;

    private final Long id;
    private final String username;
    private String passwordHash;
    private final boolean enabled;
    private final boolean locked;
    private final boolean mustChangePassword;
    private final boolean twoFactorEnabled;
    private final String displayName;
    private final Language language;
    private final Long companyId;
    private final String companyName;
    private final String companySchema;
    private final String profileName;
    private final Set<GrantedAuthority> authorities;

    private AppUserPrincipal(Long id, String username, String passwordHash, boolean enabled, boolean locked,
                             boolean mustChangePassword, boolean twoFactorEnabled, String displayName, Language language,
                             Long companyId, String companyName, String companySchema, String profileName,
                             Set<GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.locked = locked;
        this.mustChangePassword = mustChangePassword;
        this.twoFactorEnabled = twoFactorEnabled;
        this.displayName = displayName;
        this.language = language;
        this.companyId = companyId;
        this.companyName = companyName;
        this.companySchema = companySchema;
        this.profileName = profileName;
        this.authorities = Set.copyOf(authorities);
    }

    /**
     * The user working in {@code access}'s company, with the privileges of their profile there; {@code access} null:
     * no company chosen yet. The access must have its company, profile and privileges loaded.
     */
    public static AppUserPrincipal of(User user, CompanyAccess access, LocalDateTime now) {
        return new AppUserPrincipal(user.getId(), user.getUsername(), user.getPassword(), user.isEnabled(),
            user.isLocked(now), user.isMustChangePassword(), user.isTotpEnabled(), user.getDisplayName(), user.getLanguage(),
            null, null, null, null, Set.of()).withCompany(access);
    }

    /** Same user, working in another company (or in none). */
    public AppUserPrincipal withCompany(CompanyAccess access) {
        if (access == null) {
            return new AppUserPrincipal(id, username, passwordHash, enabled, locked, mustChangePassword, twoFactorEnabled,
                displayName, language, null, null, null, null, Set.of());
        }
        Set<GrantedAuthority> privileges = access.getProfile().getPrivileges().stream()
            .map(Privilege::getCode)
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toSet());
        Company company = access.getCompany();
        return new AppUserPrincipal(id, username, passwordHash, enabled, locked, mustChangePassword, twoFactorEnabled,
            displayName, language, company.getId(), company.getName(), company.getSchemaName(), access.getProfile().getName(),
            privileges);
    }

    /** Same user once the forced password change is done. */
    public AppUserPrincipal withPasswordChanged() {
        return new AppUserPrincipal(id, username, null, enabled, locked, false, twoFactorEnabled, displayName, language,
            companyId, companyName, companySchema, profileName, authorities);
    }

    /** Same user once two-factor login has been turned on or off. */
    public AppUserPrincipal withTwoFactorEnabled(boolean enabled) {
        return new AppUserPrincipal(id, username, null, this.enabled, locked, mustChangePassword, enabled, displayName, language,
            companyId, companyName, companySchema, profileName, authorities);
    }

    /** Same user after editing their personal information. */
    public AppUserPrincipal withDisplayName(String displayName) {
        return new AppUserPrincipal(id, username, null, enabled, locked, mustChangePassword, twoFactorEnabled, displayName, language,
            companyId, companyName, companySchema, profileName, authorities);
    }

    /** Same user after choosing another language. */
    public AppUserPrincipal withLanguage(Language language) {
        return new AppUserPrincipal(id, username, null, enabled, locked, mustChangePassword, twoFactorEnabled, displayName, language,
            companyId, companyName, companySchema, profileName, authorities);
    }

    public Long getId() {
        return id;
    }

    /** The profile in the current company; null without a company. */
    public String getProfileName() {
        return profileName;
    }

    /** The company the user works in; null until one is chosen. */
    public Long getCompanyId() {
        return companyId;
    }

    public String getCompanyName() {
        return companyName;
    }

    /** The schema of the current company (see CompanySchemas); null until one is chosen. */
    public String getCompanySchema() {
        return companySchema;
    }

    public boolean hasCompany() {
        return companyId != null;
    }

    /** The first name, or the username when it is not filled in. */
    public String getDisplayName() {
        return displayName;
    }

    /** The language of the interface for this user. */
    public Language getLanguage() {
        return language;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public boolean isTwoFactorEnabled() {
        return twoFactorEnabled;
    }

    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /** Identity is the username: the session registry finds a user's sessions by principal. */
    @Override
    public boolean equals(Object other) {
        return other instanceof AppUserPrincipal that && username.equals(that.username);
    }

    @Override
    public int hashCode() {
        return username.hashCode();
    }

    /** Called by Spring Security after authentication: the hash does not stay in the session. */
    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
