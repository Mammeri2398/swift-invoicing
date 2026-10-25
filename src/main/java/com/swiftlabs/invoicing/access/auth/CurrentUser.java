package com.swiftlabs.invoicing.access.auth;

import java.util.Set;

/** The logged-in user as seen by views: name, profile and privilege checks. */
public final class CurrentUser {

    private final String username;
    private final String displayName;
    private final String profileName;
    private final Set<String> privileges;

    public CurrentUser(String username, String displayName, String profileName, Set<String> privileges) {
        this.username = username;
        this.displayName = displayName;
        this.profileName = profileName;
        this.privileges = Set.copyOf(privileges);
    }

    public String getUsername() {
        return username;
    }

    /** The first name, or the username when it is not filled in. */
    public String getDisplayName() {
        return displayName;
    }

    public String getProfileName() {
        return profileName;
    }

    /** Used by templates: {@code th:if="${currentUser.can('MANAGE_USERS')}"}. */
    public boolean can(String privilege) {
        return privileges.contains(privilege);
    }

    public boolean canAny(String... codes) {
        for (String code : codes) {
            if (privileges.contains(code)) {
                return true;
            }
        }
        return false;
    }
}
