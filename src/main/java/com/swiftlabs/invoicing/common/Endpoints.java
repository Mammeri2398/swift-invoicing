package com.swiftlabs.invoicing.common;

import java.util.List;

/**
 * Every URL of the application, for the Java code (controllers, redirects, security, filters).
 * Templates keep their own @{/...} links.
 *
 * A controller's base path is a full path (CLIENTS = "/clients"); the paths of its methods are relative to it
 * (CLIENT_EDIT = "/{id}/edit" means /clients/{id}/edit). A redirect is "redirect:" + a full path.
 */
public final class Endpoints {

    private Endpoints() {
    }

    // ---- Static files and technical URLs ----------------------------------------------------------------------
    public static final String CSS_FOLDER = "/css/";
    public static final String JS_FOLDER = "/js/";
    public static final String FONTS_FOLDER = "/fonts/";
    public static final String IMAGES_FOLDER = "/images/";
    /** The static files: always served, before login, setup or any other check. */
    public static final List<String> STATIC_FOLDERS = List.of(CSS_FOLDER, JS_FOLDER, FONTS_FOLDER, IMAGES_FOLDER);
    public static final String CSS = CSS_FOLDER + "**";
    public static final String JS = JS_FOLDER + "**";
    public static final String FONTS = FONTS_FOLDER + "**";
    public static final String IMAGES = IMAGES_FOLDER + "**";
    public static final String FAVICON = "/favicon.ico";
    public static final String ERROR = "/error";
    public static final String HEALTH = "/actuator/health";
    public static final String HEALTH_LIVENESS = "/actuator/health/liveness";
    public static final String HEALTH_READINESS = "/actuator/health/readiness";

    // ---- Home ---------------------------------------------------------------------------------------------------
    public static final String HOME = "/";
    public static final String DASHBOARD = "/dashboard";
    public static final String TURNOVER = "/turnover";

    // ---- Login, logout, password reset --------------------------------------------------------------------------
    public static final String LOGIN = "/login";
    /** Handled by Spring Security (the login form posts here). */
    public static final String PERFORM_LOGIN = "/perform-login";
    public static final String PERFORM_LOGOUT = "/perform-logout";
    public static final String LOGIN_VERIFY = "/login/verify";
    public static final String ACCESS_DENIED = "/access-denied";
    public static final String FORGOT_PASSWORD = "/forgot-password";
    public static final String FORGOT_PASSWORD_SEND = "/forgot-password/send";
    public static final String RESET_PASSWORD = "/reset-password";
    public static final String RESET_PASSWORD_SAVE = "/reset-password/save";

    // ---- First start (on-premise setup wizard) ------------------------------------------------------------------
    public static final String SETUP = "/setup";

    // ---- My account ----------------------------------------------------------------------------------------------
    public static final String ACCOUNT = "/account";
    public static final String ACCOUNT_SAVE = "/save";
    public static final String ACCOUNT_LANGUAGE = "/language";
    public static final String ACCOUNT_PASSWORD = "/password";
    /** Full path of the forced password change page. */
    public static final String ACCOUNT_PASSWORD_PAGE = ACCOUNT + ACCOUNT_PASSWORD;

    public static final String TWO_FACTOR = "/account/two-factor";
    public static final String TWO_FACTOR_CONFIRM = "/confirm";
    public static final String TWO_FACTOR_DISABLE = "/disable";
    public static final String TWO_FACTOR_RECOVERY_CODES = "/recovery-codes";

    // ---- Users, profiles, privileges ----------------------------------------------------------------------------
    public static final String USERS = "/users";
    public static final String USER_NEW = "/new";
    public static final String USER_SAVE = "/save";
    public static final String USER_EDIT = "/{id}/edit";
    public static final String USER_DELETE = "/{id}/delete";
    public static final String USER_RESET_PASSWORD = "/{id}/reset-password";
    public static final String USER_RESET_TWO_FACTOR = "/{id}/reset-two-factor";

    public static final String PROFILES = "/profiles";
    public static final String PROFILE_NEW = "/new";
    public static final String PROFILE_SAVE = "/save";
    public static final String PROFILE_EDIT = "/{id}/edit";
    public static final String PROFILE_DELETE = "/{id}/delete";

    public static final String PRIVILEGES = "/privileges";
    public static final String PRIVILEGE_NEW = "/new";
    public static final String PRIVILEGE_SAVE = "/save";
    public static final String PRIVILEGE_EDIT = "/{id}/edit";
    public static final String PRIVILEGE_DELETE = "/{id}/delete";

    // ---- Clients --------------------------------------------------------------------------------------------------
    public static final String CLIENTS = "/clients";
    public static final String CLIENT_NEW = "/new";
    public static final String CLIENT_SAVE = "/save";
    public static final String CLIENT_EDIT = "/{id}/edit";
    public static final String CLIENT_DELETE = "/{id}/delete";
    /** Full path. */
    public static final String CLIENT_STATEMENT = "/clients/{id}/statement";

    // ---- Catalogue of products and services ------------------------------------------------------------------------
    public static final String CATALOG = "/catalog";
    public static final String CATALOG_NEW = "/new";
    public static final String CATALOG_SAVE = "/save";
    public static final String CATALOG_EDIT = "/{id}/edit";
    public static final String CATALOG_DELETE = "/{id}/delete";

    // ---- Invoices, credit notes, quotes ---------------------------------------------------------------------------
    public static final String INVOICES = "/invoices";
    public static final String INVOICE_NEW = "/new";
    public static final String INVOICE_SAVE = "/save";
    public static final String INVOICE_VIEW = "/{id}";
    public static final String INVOICE_EDIT = "/{id}/edit";
    public static final String INVOICE_DELETE = "/{id}/delete";
    public static final String INVOICE_PDF = "/{id}/pdf";
    public static final String INVOICE_ISSUE = "/{id}/issue";
    public static final String INVOICE_PAY = "/{id}/pay";
    public static final String INVOICE_CANCEL = "/{id}/cancel";
    public static final String INVOICE_CREDIT_NOTE = "/{id}/credit-note";
    public static final String INVOICE_PAYMENTS = "/{id}/payments";
    public static final String INVOICE_PAYMENT_DELETE = "/{id}/payments/{paymentId}/delete";
    public static final String INVOICE_ACCEPT = "/{id}/accept";
    public static final String INVOICE_REFUSE = "/{id}/refuse";
    public static final String INVOICE_CONVERT = "/{id}/convert";

    /** Full path: the public page that checks an invoice number. */
    public static final String INVOICE_LOOKUP = "/invoices/lookup";

    public static final String INVOICE_EXPORT = "/invoices/export";
    public static final String INVOICE_EXPORT_CSV = "/csv";
    public static final String INVOICE_EXPORT_XLSX = "/xlsx";

    public static final String INVOICE_EMAIL = "/invoices/{id}/email";
    public static final String INVOICE_EMAIL_SEND = "/send";

    public static final String QUOTES = "/quotes";

    // ---- Recurring invoices -------------------------------------------------------------------------------------------
    public static final String RECURRING = "/recurring";
    public static final String RECURRING_NEW = "/new";
    public static final String RECURRING_SAVE = "/save";
    public static final String RECURRING_EDIT = "/{id}/edit";
    public static final String RECURRING_PAUSE = "/{id}/pause";
    public static final String RECURRING_RESUME = "/{id}/resume";
    public static final String RECURRING_DELETE = "/{id}/delete";
    public static final String RECURRING_RUN = "/run";

    // ---- TVA du mois (help for the G50 declaration) -------------------------------------------------------------
    public static final String VAT = "/vat";
    public static final String VAT_EXPORT = "/export";

    // ---- Comptabilité (export for the accountant) -------------------------------------------------------------------
    public static final String ACCOUNTING = "/accounting";
    public static final String ACCOUNTING_XLSX = "/export/xlsx";
    public static final String ACCOUNTING_CSV = "/export/csv";
    public static final String ACCOUNTING_ACCOUNTS = "/accounts";

    // ---- Help -------------------------------------------------------------------------------------------------------
    public static final String HELP = "/help";

    // ---- Settings ---------------------------------------------------------------------------------------------------
    public static final String SETTINGS = "/settings";

    public static final String ISSUER = "/settings/issuer";
    public static final String ISSUER_SAVE = "/save";
    public static final String ISSUER_LOGO = "/logo";
    public static final String ISSUER_LOGO_DELETE = "/logo/delete";

    public static final String MAIL_SETTINGS = "/settings/mail";
    public static final String MAIL_SETTINGS_TEST = "/test";

    public static final String LICENCE = "/settings/licence";

    public static final String BACKUPS = "/settings/backups";
    public static final String BACKUP_NOW = "/now";
    public static final String BACKUP_SETTINGS = "/settings";
    public static final String BACKUP_IMPORT = "/import";
    public static final String BACKUP_FILE = "/{name}";
    public static final String BACKUP_DELETE = "/{name}/delete";
    public static final String BACKUP_RESTORE = "/{name}/restore";

    /** "redirect:" + a full path: the answer of a controller method that sends the browser elsewhere. */
    public static final String REDIRECT = "redirect:";

    /**
     * A path with its {placeholders} filled in order:
     * {@code Endpoints.of(Endpoints.INVOICES + Endpoints.INVOICE_EDIT, 12)} is "/invoices/12/edit".
     */
    public static String of(String path, Object... values) {
        String result = path;
        for (Object value : values) {
            result = result.replaceFirst("\\{[^}/]+}", String.valueOf(value));
        }
        return result;
    }
}
