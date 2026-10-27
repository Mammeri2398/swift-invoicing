package com.swiftlabs.invoicing.common;

/**
 * Names shared between the Java code and the templates: the attributes a controller puts in the model (or in
 * the flash attributes of a redirect) and the views (templates) it renders. See also {@link Endpoints} for the
 * URLs, {@link Privileges} for the privilege codes and {@link MessageProperties} for the message keys.
 */
public final class CommonCodes {

    private CommonCodes() {
    }

    /** Model and flash attributes: the ${name} a template reads. */
    public static final class Attributes {

        private Attributes() {
        }

        public static final String ACCOUNT = "account";
        public static final String ACCOUNTS = "accounts";
        public static final String ACTIVE_USERS = "activeUsers";
        public static final String ALL_PRIVILEGES = "allPrivileges";
        public static final String AMOUNT_IN_WORDS = "amountInWords";
        public static final String AMOUNT_UTILS = "amountUtils";
        public static final String AVAILABLE = "available";
        public static final String BACKUPS = "backups";
        public static final String BACKUP_DIRECTORY = "backupDirectory";
        public static final String BACKUP_WARNING = "backupWarning";
        public static final String BALANCES = "balances";
        public static final String BASES = "bases";
        public static final String CAN_EDIT = "canEdit";
        public static final String CATALOG_ITEMS = "catalogItems";
        public static final String CLIENT = "client";
        public static final String CLIENTS = "clients";
        public static final String COMPANIES = "companies";
        public static final String COMPANY = "company";
        public static final String COMPANY_TYPE = "companyType";
        public static final String CONFIRMATION = "confirmation";
        public static final String CREDIT_NOTES = "creditNotes";
        public static final String CREDIT_NOTE_OF = "creditNoteOf";
        public static final String CURRENT_PATH = "currentPath";
        public static final String CURRENT_USER = "currentUser";
        public static final String DEFAULT_VAT_MENTION = "defaultVatMention";
        public static final String DIRECTORY = "directory";
        public static final String DOC_TITLE = "docTitle";
        public static final String ENABLED = "enabled";
        public static final String ERROR = "error";
        public static final String FAILURE = "failure";
        public static final String FILTER = "filter";
        public static final String FORM_KIND = "formKind";
        public static final String FORM_TITLE = "formTitle";
        public static final String FREQUENCIES = "frequencies";
        public static final String FROM_ADDRESS = "fromAddress";
        public static final String FULLY_CREDITED = "fullyCredited";
        public static final String HAS_LOGO = "hasLogo";
        public static final String HAS_PASSWORD = "hasPassword";
        public static final String HISTORY = "history";
        public static final String INITIALS = "initials";
        public static final String INVOICE = "invoice";
        public static final String INVOICE_ID = "invoiceId";
        public static final String INVOICE_OF = "invoiceOf";
        public static final String ISSUER = "issuer";
        public static final String ITEM = "item";
        public static final String ITEMS = "items";
        public static final String JOURNAL = "journal";
        public static final String LANGUAGES = "languages";
        public static final String LICENCE = "licence";
        public static final String LINK_VALIDITY_MINUTES = "linkValidityMinutes";
        public static final String LOGOUT = "logout";
        public static final String LOGO_DATA_URI = "logoDataUri";
        public static final String MAIL = "mail";
        public static final String MAIL_ENABLED = "mailEnabled";
        public static final String MAX_USERS = "maxUsers";
        public static final String MESSAGE = "message";
        public static final String MONTHS = "months";
        public static final String MONTH_NAMES = "monthNames";
        public static final String NAV_SECTION = "navSection";
        public static final String NETWORK_ADDRESSES = "networkAddresses";
        public static final String NOW = "now";
        public static final String NUMBER = "number";
        public static final String OPEN_PASSWORD = "openPassword";
        public static final String OVERDUE = "overdue";
        public static final String OVERDUE_TOTAL = "overdueTotal";
        public static final String PAGE = "page";
        public static final String PAGE_LINKS = "pageLinks";
        public static final String PASSWORD_RULES = "passwordRules";
        public static final String PAYMENT = "payment";
        public static final String PAYMENTS = "payments";
        public static final String PAYMENT_FORM = "paymentForm";
        public static final String PAYMENT_METHODS = "paymentMethods";
        public static final String PAYMENT_TERMS = "paymentTerms";
        public static final String PERIODS = "periods";
        public static final String PREVIEW_LIMIT = "previewLimit";
        public static final String PRIVILEGE = "privilege";
        public static final String PRIVILEGES = "privileges";
        public static final String PROFILE = "profile";
        public static final String PROFILES = "profiles";
        public static final String PROFILE_NAMES = "profileNames";
        public static final String RECOVERY_CODES = "recoveryCodes";
        public static final String RECURRING = "recurring";
        public static final String REFUSED = "refused";
        public static final String REMAINING_CODES = "remainingCodes";
        public static final String REMAINING_TO_CREDIT = "remainingToCredit";
        public static final String REPORT = "report";
        public static final String REQUIRED = "required";
        public static final String SECURITIES = "securities";
        public static final String SETTINGS = "settings";
        public static final String SETUP = "setup";
        public static final String STAMP_DUTY_RULES = "stampDutyRules";
        public static final String STATEMENT = "statement";
        public static final String STATS = "stats";
        public static final String STATUS = "status";
        public static final String STATUSES = "statuses";
        public static final String SUCCESS = "success";
        public static final String SUMMARY = "summary";
        public static final String SUPPORT_CONTACT = "supportContact";
        public static final String TEMPORARY_PASSWORD = "temporaryPassword";
        public static final String TEMPORARY_PASSWORD_USER = "temporaryPasswordUser";
        public static final String TOKEN = "token";
        public static final String TOTAL = "total";
        public static final String TYPES = "types";
        public static final String USER = "user";
        public static final String USERS = "users";
        public static final String VAT_MENTION = "vatMention";
        public static final String VAT_RATES = "vatRates";
        public static final String VERIFICATION = "verification";
        public static final String VERSION = "version";
        public static final String YEARS = "years";
    }

    /** Views: templates/<name>.html. */
    public static final class Views {

        private Views() {
        }

        // access
        public static final String ACCESS_ACCESS_DENIED = "access/access-denied";
        public static final String ACCESS_ACCOUNT_INDEX = "access/account/index";
        public static final String ACCESS_ACCOUNT_PASSWORD = "access/account/password";
        public static final String ACCESS_ACCOUNT_TWO_FACTOR = "access/account/two-factor";
        public static final String ACCESS_FORGOT_PASSWORD = "access/forgot-password";
        public static final String ACCESS_LOGIN = "access/login";
        public static final String ACCESS_PRIVILEGE_FORM = "access/privilege/form";
        public static final String ACCESS_PRIVILEGE_LIST = "access/privilege/list";
        public static final String ACCESS_PROFILE_FORM = "access/profile/form";
        public static final String ACCESS_PROFILE_LIST = "access/profile/list";
        public static final String ACCESS_RESET_PASSWORD = "access/reset-password";
        public static final String ACCESS_TWO_FACTOR_VERIFY = "access/two-factor-verify";
        public static final String ACCESS_USER_FORM = "access/user/form";
        public static final String ACCESS_USER_LIST = "access/user/list";

        // accounting
        public static final String ACCOUNTING_INDEX = "accounting/index";

        // backup
        public static final String BACKUP_BACKUPS = "backup/backups";

        // catalog
        public static final String CATALOG_FORM = "catalog/form";
        public static final String CATALOG_LIST = "catalog/list";

        // client
        public static final String CLIENT_FORM = "client/form";
        public static final String CLIENT_LIST = "client/list";

        // company
        public static final String COMPANY_SELECT = "company/select";

        // dashboard
        public static final String DASHBOARD_INDEX = "dashboard/index";
        public static final String DASHBOARD_TURNOVER = "dashboard/turnover";

        // error
        public static final String ERROR = "error";

        // help
        public static final String HELP_INDEX = "help/index";

        // invoice
        public static final String INVOICE_EMAIL = "invoice/email";
        public static final String INVOICE_FORM = "invoice/form";
        public static final String INVOICE_LIST = "invoice/list";
        public static final String INVOICE_LOOKUP = "invoice/lookup";
        public static final String INVOICE_QUOTES = "invoice/quotes";
        public static final String INVOICE_STATEMENT = "invoice/statement";
        public static final String INVOICE_VIEW = "invoice/view";

        // issuer
        public static final String ISSUER_FORM = "issuer/form";

        // licence
        public static final String LICENCE_LICENCE = "licence/licence";

        // mail
        public static final String MAIL_SETTINGS = "mail/settings";

        // recurring
        public static final String RECURRING_FORM = "recurring/form";
        public static final String RECURRING_LIST = "recurring/list";

        // setup
        public static final String SETUP_SETUP = "setup/setup";

        // vat
        public static final String VAT_INDEX = "vat/index";
    }
}
