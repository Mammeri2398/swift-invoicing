package com.swiftlabs.invoicing.common;

/**
 * The privilege codes (table privilege, seeded by the migrations). A profile holds privileges; a user
 * has the privileges of their profile, as Spring Security authorities.
 *
 * In Java: {@code user.can(Privileges.VIEW_ISSUER)}. On a controller: {@code @PreAuthorize(Privileges.Has.VIEW_ISSUER)}.
 */
public final class Privileges {

    private Privileges() {
    }

    // ---- Dashboard ---------------------------------------------------------------------------------------------
    public static final String VIEW_DASHBOARD = "VIEW_DASHBOARD";

    // ---- Invoices, credit notes, quotes ------------------------------------------------------------------------
    public static final String VIEW_INVOICES = "VIEW_INVOICES";
    public static final String CREATE_INVOICE = "CREATE_INVOICE";
    public static final String ISSUE_INVOICE = "ISSUE_INVOICE";
    public static final String DELETE_INVOICE = "DELETE_INVOICE";

    // ---- Catalogue of products and services --------------------------------------------------------------------
    public static final String MANAGE_CATALOG = "MANAGE_CATALOG";

    // ---- Clients ----------------------------------------------------------------------------------------------
    public static final String VIEW_CLIENTS = "VIEW_CLIENTS";
    public static final String CREATE_CLIENT = "CREATE_CLIENT";
    public static final String DELETE_CLIENT = "DELETE_CLIENT";

    // ---- Settings (company profile, email, licence) -----------------------------------------------------------
    public static final String VIEW_ISSUER = "VIEW_ISSUER";
    public static final String EDIT_ISSUER = "EDIT_ISSUER";

    // ---- Administration ---------------------------------------------------------------------------------------
    public static final String MANAGE_USERS = "MANAGE_USERS";
    public static final String MANAGE_PROFILES = "MANAGE_PROFILES";
    public static final String MANAGE_PRIVILEGES = "MANAGE_PRIVILEGES";
    public static final String MANAGE_BACKUPS = "MANAGE_BACKUPS";
    public static final String MANAGE_COMPANIES = "MANAGE_COMPANIES";
    public static final String VIEW_AUDIT = "VIEW_AUDIT";

    /** The same privileges as Spring Security expressions, for @PreAuthorize. */
    public static final class Has {

        private Has() {
        }

        private static final String PREFIX = "hasAuthority('";
        private static final String SUFFIX = "')";

        public static final String VIEW_DASHBOARD = PREFIX + Privileges.VIEW_DASHBOARD + SUFFIX;
        public static final String VIEW_INVOICES = PREFIX + Privileges.VIEW_INVOICES + SUFFIX;
        public static final String CREATE_INVOICE = PREFIX + Privileges.CREATE_INVOICE + SUFFIX;
        public static final String ISSUE_INVOICE = PREFIX + Privileges.ISSUE_INVOICE + SUFFIX;
        public static final String DELETE_INVOICE = PREFIX + Privileges.DELETE_INVOICE + SUFFIX;
        public static final String MANAGE_CATALOG = PREFIX + Privileges.MANAGE_CATALOG + SUFFIX;
        public static final String VIEW_CLIENTS = PREFIX + Privileges.VIEW_CLIENTS + SUFFIX;
        public static final String CREATE_CLIENT = PREFIX + Privileges.CREATE_CLIENT + SUFFIX;
        public static final String DELETE_CLIENT = PREFIX + Privileges.DELETE_CLIENT + SUFFIX;
        public static final String VIEW_ISSUER = PREFIX + Privileges.VIEW_ISSUER + SUFFIX;
        public static final String EDIT_ISSUER = PREFIX + Privileges.EDIT_ISSUER + SUFFIX;
        public static final String MANAGE_USERS = PREFIX + Privileges.MANAGE_USERS + SUFFIX;
        public static final String MANAGE_PROFILES = PREFIX + Privileges.MANAGE_PROFILES + SUFFIX;
        public static final String MANAGE_PRIVILEGES = PREFIX + Privileges.MANAGE_PRIVILEGES + SUFFIX;
        public static final String MANAGE_BACKUPS = PREFIX + Privileges.MANAGE_BACKUPS + SUFFIX;
        public static final String MANAGE_COMPANIES = PREFIX + Privileges.MANAGE_COMPANIES + SUFFIX;
        public static final String VIEW_AUDIT = PREFIX + Privileges.VIEW_AUDIT + SUFFIX;
    }
}
