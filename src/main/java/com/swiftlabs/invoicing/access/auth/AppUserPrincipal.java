package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.access.privilege.Privilege;
import com.swiftlabs.invoicing.access.user.User;

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
 */
public final class AppUserPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String username;
    private String passwordHash;
    private final boolean enabled;
    private final boolean locked;
    private final boolean mustChangePassword;
    private final String profileName;
    private final Set<GrantedAuthority> authorities;

    private AppUserPrincipal(Long id, String username, String passwordHash, boolean enabled, boolean locked,
                             boolean mustChangePassword, String profileName, Set<GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.locked = locked;
        this.mustChangePassword = mustChangePassword;
        this.profileName = profileName;
        this.authorities = Set.copyOf(authorities);
    }

    /** The user must have its profile and privileges loaded. */
    public static AppUserPrincipal of(User user, LocalDateTime now) {
        Set<GrantedAuthority> authorities = user.getProfile().getPrivileges().stream()
            .map(Privilege::getCode)
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toSet());
        return new AppUserPrincipal(user.getId(), user.getUsername(), user.getPassword(), user.isEnabled(),
            user.isLocked(now), user.isMustChangePassword(), user.getProfile().getName(), authorities);
    }

    /** Same user once the forced password change is done. */
    public AppUserPrincipal withPasswordChanged() {
        return new AppUserPrincipal(id, username, null, enabled, locked, false, profileName, authorities);
    }

    public Long getId() {
        return id;
    }

    public String getProfileName() {
        return profileName;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
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
