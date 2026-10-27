package com.swiftlabs.invoicing.access.auth;

import java.util.Set;

/** The logged-in user as seen by views: name, company, profile and privilege checks. */
public final class CurrentUser {

    private final String username;
    private final String displayName;
    private final String profileName;
    private final Long companyId;
    private final String companyName;
    private final Set<String> privileges;

    public CurrentUser(String username, String displayName, String profileName, Long companyId, String companyName,
                       Set<String> privileges) {
        this.username = username;
        this.displayName = displayName;
        this.profileName = profileName;
        this.companyId = companyId;
        this.companyName = companyName;
        this.privileges = Set.copyOf(privileges);
    }

    public String getUsername() {
        return username;
    }

    /** The first name, or the username when it is not filled in. */
    public String getDisplayName() {
        return displayName;
    }

    /** The profile in the current company. */
    public String getProfileName() {
        return profileName;
    }

    /** The company the user works in (header, company switcher); null until chosen. */
    public Long getCompanyId() {
        return companyId;
    }

    public String getCompanyName() {
        return companyName;
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
