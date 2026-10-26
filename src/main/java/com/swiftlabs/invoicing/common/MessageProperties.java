package com.swiftlabs.invoicing.common;

/**
 * The message keys used by the Java code (flash messages, business errors, emails...), grouped by area.
 * The texts are in src/main/resources/i18n/*.properties and messages*.properties, in French, English and
 * Arabic: read them with {@link Messages#get}, e.g. {@code messages.get(MessageProperties.ACCOUNT_SAVED)}.
 * Templates use the keys directly (#{account.saved}).
 */
public final class MessageProperties {

    private MessageProperties() {
    }

    // ---- account ---------------------------------------------------------------------------------------------
    public static final String ACCOUNT_LANGUAGE_SAVED = "account.language.saved";
    public static final String ACCOUNT_PASSWORD_CHANGED = "account.password.changed";
    public static final String ACCOUNT_PASSWORD_MISMATCH = "account.password.mismatch";
    public static final String ACCOUNT_SAVED = "account.saved";

    // ---- auth ------------------------------------------------------------------------------------------------
    public static final String AUTH_FORGOT_SENT = "auth.forgot.sent";
    public static final String AUTH_LOGIN_ERROR = "auth.login.error";
    public static final String AUTH_LOGIN_EXPIRED = "auth.login.expired";
    public static final String AUTH_LOGIN_LOGGED_OUT = "auth.login.loggedOut";
    public static final String AUTH_LOGIN_RESTORED = "auth.login.restored";
    public static final String AUTH_LOGIN_SETUP_DONE = "auth.login.setupDone";
    public static final String AUTH_RESET_DONE = "auth.reset.done";
    public static final String AUTH_RESET_INVALID_LINK = "auth.reset.invalidLink";
    public static final String AUTH_RESET_MAIL_BODY = "auth.reset.mail.body";
    public static final String AUTH_RESET_MAIL_SUBJECT = "auth.reset.mail.subject";
    public static final String AUTH_RESET_MISMATCH = "auth.reset.mismatch";

    // ---- backup ----------------------------------------------------------------------------------------------
    public static final String BACKUP_DELETED = "backup.deleted";
    public static final String BACKUP_DONE = "backup.done";
    public static final String BACKUP_ERROR_BACKUP_NOT_READ = "backup.error.backupNotRead";
    public static final String BACKUP_ERROR_BUSY = "backup.error.busy";
    public static final String BACKUP_ERROR_CHOOSE_FILE = "backup.error.chooseFile";
    public static final String BACKUP_ERROR_DIRECTORY_CREATE = "backup.error.directoryCreate";
    public static final String BACKUP_ERROR_DIRECTORY_NOT_WRITABLE = "backup.error.directoryNotWritable";
    public static final String BACKUP_ERROR_DIRECTORY_REQUIRED = "backup.error.directoryRequired";
    public static final String BACKUP_ERROR_FILE_NOT_SAVED = "backup.error.fileNotSaved";
    public static final String BACKUP_ERROR_FILE_UNREADABLE = "backup.error.fileUnreadable";
    public static final String BACKUP_ERROR_FOLDER_INACCESSIBLE = "backup.error.folderInaccessible";
    public static final String BACKUP_ERROR_FOLDER_NOT_WRITABLE = "backup.error.folderNotWritable";
    public static final String BACKUP_ERROR_KEEP_COUNT = "backup.error.keepCount";
    public static final String BACKUP_ERROR_NEEDS_POSTGRES = "backup.error.needsPostgres";
    public static final String BACKUP_ERROR_NOT_ENABLED = "backup.error.notEnabled";
    public static final String BACKUP_ERROR_NOT_FOUND = "backup.error.notFound";
    public static final String BACKUP_ERROR_NOT_OURS = "backup.error.notOurs";
    public static final String BACKUP_ERROR_TOOL_MISSING = "backup.error.toolMissing";
    public static final String BACKUP_ERROR_UNREADABLE_BACKUP = "backup.error.unreadableBackup";
    public static final String BACKUP_IMPORTED = "backup.imported";
    public static final String BACKUP_RESTORE_WORD = "backup.restore.word";
    public static final String BACKUP_RESTORE_CANCELLED = "backup.restoreCancelled";
    public static final String BACKUP_SETTINGS_SAVED = "backup.settingsSaved";
    public static final String BACKUP_SIZE_KB = "backup.size.kb";
    public static final String BACKUP_SIZE_MB = "backup.size.mb";
    public static final String BACKUP_TOOL_DUMP = "backup.tool.dump";
    public static final String BACKUP_TOOL_FAILED = "backup.tool.failed";
    public static final String BACKUP_TOOL_FAILED_DETAIL = "backup.tool.failedDetail";
    public static final String BACKUP_TOOL_INTERRUPTED = "backup.tool.interrupted";
    public static final String BACKUP_TOOL_LIST = "backup.tool.list";
    public static final String BACKUP_TOOL_READ = "backup.tool.read";
    public static final String BACKUP_TOOL_RESTORE = "backup.tool.restore";
    public static final String BACKUP_TOOL_TIMEOUT = "backup.tool.timeout";
    public static final String BACKUP_WARNING = "backup.warning";
    public static final String BACKUP_WARNING_NONE = "backup.warning.none";
    public static final String BACKUP_WARNING_STALE = "backup.warning.stale";

    // ---- client ----------------------------------------------------------------------------------------------
    public static final String CLIENT_DELETED = "client.deleted";
    public static final String CLIENT_ERROR_HAS_INVOICES = "client.error.hasInvoices";
    public static final String CLIENT_ERROR_NIF_REQUIRED = "client.error.nifRequired";
    public static final String CLIENT_ERROR_NOT_FOUND = "client.error.notFound";
    public static final String CLIENT_ERROR_TYPE_REQUIRED = "client.error.typeRequired";
    public static final String CLIENT_SAVED = "client.saved";

    // ---- clientStatement -------------------------------------------------------------------------------------
    public static final String CLIENT_STATEMENT_ENTRY_CREDIT_NOTE = "clientStatement.entry.creditNote";
    public static final String CLIENT_STATEMENT_ENTRY_INVOICE = "clientStatement.entry.invoice";
    public static final String CLIENT_STATEMENT_ENTRY_PAYMENT = "clientStatement.entry.payment";
    public static final String CLIENT_STATEMENT_ENTRY_PAYMENT_BY = "clientStatement.entry.paymentBy";
    public static final String CLIENT_STATEMENT_ENTRY_WITH_REFERENCE = "clientStatement.entry.withReference";

    // ---- error -----------------------------------------------------------------------------------------------
    public static final String ERROR_UNEXPECTED = "error.unexpected";
    public static final String ERROR_UPLOAD_TOO_LARGE = "error.uploadTooLarge";

    // ---- invoice ---------------------------------------------------------------------------------------------
    public static final String INVOICE_ERROR_ALREADY_CANCELLED = "invoice.error.alreadyCancelled";
    public static final String INVOICE_ERROR_ALREADY_ISSUED = "invoice.error.alreadyIssued";
    public static final String INVOICE_ERROR_CLIENT_MISSING_AI = "invoice.error.clientMissing.ai";
    public static final String INVOICE_ERROR_CLIENT_MISSING_BOTH = "invoice.error.clientMissing.both";
    public static final String INVOICE_ERROR_CLIENT_MISSING_RC = "invoice.error.clientMissing.rc";
    public static final String INVOICE_ERROR_CLIENT_NIF_REQUIRED = "invoice.error.clientNifRequired";
    public static final String INVOICE_ERROR_CLIENT_REQUIRED = "invoice.error.clientRequired";
    public static final String INVOICE_ERROR_CREDIT_NOTE_BEFORE_INVOICE = "invoice.error.creditNoteBeforeInvoice";
    public static final String INVOICE_ERROR_CREDIT_NOTE_NOT_ALLOWED = "invoice.error.creditNoteNotAllowed";
    public static final String INVOICE_ERROR_CREDIT_NOTE_NOT_CANCELLABLE = "invoice.error.creditNoteNotCancellable";
    public static final String INVOICE_ERROR_CREDIT_NOTE_NOT_PAYABLE = "invoice.error.creditNoteNotPayable";
    public static final String INVOICE_ERROR_CREDIT_NOTE_TOO_HIGH = "invoice.error.creditNoteTooHigh";
    public static final String INVOICE_ERROR_DELETE_DRAFT_ONLY = "invoice.error.deleteDraftOnly";
    public static final String INVOICE_ERROR_FULLY_CREDITED = "invoice.error.fullyCredited";
    public static final String INVOICE_ERROR_HAS_CREDIT_NOTE = "invoice.error.hasCreditNote";
    public static final String INVOICE_ERROR_NO_LINES = "invoice.error.noLines";
    public static final String INVOICE_ERROR_NO_LINES_TO_ISSUE = "invoice.error.noLinesToIssue";
    public static final String INVOICE_ERROR_NOT_CONVERTIBLE = "invoice.error.notConvertible";
    public static final String INVOICE_ERROR_NOT_DRAFT = "invoice.error.notDraft";
    public static final String INVOICE_ERROR_NOT_FOUND = "invoice.error.notFound";
    public static final String INVOICE_ERROR_NOTHING_TO_COLLECT = "invoice.error.nothingToCollect";
    public static final String INVOICE_ERROR_NUMBER_EXISTS = "invoice.error.numberExists";
    public static final String INVOICE_ERROR_OFFER_NOT_PAYABLE = "invoice.error.offerNotPayable";
    public static final String INVOICE_ERROR_ORIGINAL_NOT_ISSUED = "invoice.error.originalNotIssued";
    public static final String INVOICE_ERROR_PAY_ISSUED_ONLY = "invoice.error.payIssuedOnly";
    public static final String INVOICE_ERROR_QUOTE_NOT_ISSUED = "invoice.error.quoteNotIssued";
    public static final String INVOICE_ERROR_VAT_FOR_AUTO_ENTREPRENEUR = "invoice.error.vatForAutoEntrepreneur";
    public static final String INVOICE_ERROR_VAT_RATE_NOT_ALLOWED = "invoice.error.vatRateNotAllowed";
    public static final String INVOICE_ERROR_VAT_RATE_REQUIRED = "invoice.error.vatRateRequired";
    public static final String INVOICE_FLASH_ALREADY_INVOICED = "invoice.flash.alreadyInvoiced";
    public static final String INVOICE_FLASH_CANCELLED = "invoice.flash.cancelled";
    public static final String INVOICE_FLASH_CONVERTED = "invoice.flash.converted";
    public static final String INVOICE_FLASH_CREDIT_NOTE_DRAFT = "invoice.flash.creditNoteDraft";
    public static final String INVOICE_FLASH_DRAFT_DELETED = "invoice.flash.draftDeleted";
    public static final String INVOICE_FLASH_PAID = "invoice.flash.paid";
    public static final String INVOICE_FLASH_PAID_IN_FULL = "invoice.flash.paidInFull";
    public static final String INVOICE_FLASH_PAYMENT_DELETED = "invoice.flash.paymentDeleted";
    public static final String INVOICE_FLASH_PAYMENT_INVALID = "invoice.flash.paymentInvalid";
    public static final String INVOICE_FLASH_PAYMENT_RECORDED = "invoice.flash.paymentRecorded";
    public static final String INVOICE_FLASH_QUOTE_ACCEPTED = "invoice.flash.quoteAccepted";
    public static final String INVOICE_FLASH_QUOTE_REFUSED = "invoice.flash.quoteRefused";
    public static final String INVOICE_FORM_TITLE_CREDIT_NOTE = "invoice.form.title.CREDIT_NOTE";
    public static final String INVOICE_HISTORY_DRAFT_INVOICE_CREATED = "invoice.history.draftInvoiceCreated";
    public static final String INVOICE_HISTORY_FROM_PROFORMA = "invoice.history.fromProforma";
    public static final String INVOICE_HISTORY_FROM_QUOTE = "invoice.history.fromQuote";
    public static final String INVOICE_HISTORY_ON_INVOICE = "invoice.history.onInvoice";
    public static final String INVOICE_HISTORY_PAYMENT = "invoice.history.payment";
    public static final String INVOICE_HISTORY_PAYMENT_WITH = "invoice.history.paymentWith";
    public static final String INVOICE_HISTORY_SENT_TO = "invoice.history.sentTo";
    public static final String INVOICE_PAYMENT_ERROR_AMOUNT_POSITIVE = "invoice.payment.error.amountPositive";
    public static final String INVOICE_PAYMENT_ERROR_AMOUNT_TOO_HIGH = "invoice.payment.error.amountTooHigh";
    public static final String INVOICE_PAYMENT_ERROR_BEFORE_INVOICE = "invoice.payment.error.beforeInvoice";
    public static final String INVOICE_PAYMENT_ERROR_FUTURE = "invoice.payment.error.future";
    public static final String INVOICE_PAYMENT_ERROR_NOT_FOUND = "invoice.payment.error.notFound";
    public static final String INVOICE_PAYMENT_ERROR_NOT_PAYABLE = "invoice.payment.error.notPayable";
    public static final String INVOICE_PAYMENT_ERROR_NOTHING_LEFT = "invoice.payment.error.nothingLeft";
    public static final String INVOICE_VIEW_TITLE = "invoice.view.title";

    // ---- invoiceExport ---------------------------------------------------------------------------------------
    public static final String INVOICE_EXPORT_COL_BALANCE = "invoiceExport.col.balance";
    public static final String INVOICE_EXPORT_COL_PAID = "invoiceExport.col.paid";
    public static final String INVOICE_EXPORT_FILE_NAME = "invoiceExport.fileName";
    public static final String INVOICE_EXPORT_SHEET = "invoiceExport.sheet";

    // ---- invoiceMail -----------------------------------------------------------------------------------------
    public static final String INVOICE_MAIL_ERROR_DISABLED = "invoiceMail.error.disabled";
    public static final String INVOICE_MAIL_ERROR_INVALID_ADDRESS = "invoiceMail.error.invalidAddress";
    public static final String INVOICE_MAIL_ERROR_NOT_SENDABLE = "invoiceMail.error.notSendable";
    public static final String INVOICE_MAIL_ERROR_NOTHING_TO_PAY = "invoiceMail.error.nothingToPay";
    public static final String INVOICE_MAIL_ERROR_SEND_FAILED = "invoiceMail.error.sendFailed";
    public static final String INVOICE_MAIL_SENT = "invoiceMail.sent";

    // ---- issuer ----------------------------------------------------------------------------------------------
    public static final String ISSUER_ERROR_AE_CARD_REQUIRED = "issuer.error.aeCardRequired";
    public static final String ISSUER_ERROR_CEILING_POSITIVE = "issuer.error.ceilingPositive";
    public static final String ISSUER_ERROR_COMPANY_NAME_REQUIRED = "issuer.error.companyNameRequired";
    public static final String ISSUER_ERROR_FULL_NAME_REQUIRED = "issuer.error.fullNameRequired";
    public static final String ISSUER_ERROR_LEGAL_FORM_REQUIRED = "issuer.error.legalFormRequired";
    public static final String ISSUER_ERROR_LOGO_FORMAT = "issuer.error.logoFormat";
    public static final String ISSUER_ERROR_LOGO_INVALID = "issuer.error.logoInvalid";
    public static final String ISSUER_ERROR_LOGO_MISSING = "issuer.error.logoMissing";
    public static final String ISSUER_ERROR_LOGO_TOO_BIG = "issuer.error.logoTooBig";
    public static final String ISSUER_ERROR_LOGO_TOO_LARGE = "issuer.error.logoTooLarge";
    public static final String ISSUER_ERROR_NIF_REQUIRED = "issuer.error.nifRequired";
    public static final String ISSUER_ERROR_NOT_FOUND = "issuer.error.notFound";
    public static final String ISSUER_ERROR_RC_REQUIRED = "issuer.error.rcRequired";
    public static final String ISSUER_ERROR_SHARE_CAPITAL_REQUIRED = "issuer.error.shareCapitalRequired";
    public static final String ISSUER_ERROR_TAX_ARTICLE_REQUIRED = "issuer.error.taxArticleRequired";
    public static final String ISSUER_LOGO_DELETED = "issuer.logo.deleted";
    public static final String ISSUER_LOGO_SAVED = "issuer.logo.saved";
    public static final String ISSUER_LOGO_UNREADABLE = "issuer.logo.unreadable";
    public static final String ISSUER_SAVED = "issuer.saved";

    // ---- licence ---------------------------------------------------------------------------------------------
    public static final String LICENCE_ACTIVATED = "licence.activated";
    public static final String LICENCE_ERROR_BAD_SIGNATURE = "licence.error.badSignature";
    public static final String LICENCE_ERROR_EXPIRED = "licence.error.expired";
    public static final String LICENCE_ERROR_NIF_MISMATCH = "licence.error.nifMismatch";
    public static final String LICENCE_ERROR_NOT_AKEY = "licence.error.notAKey";
    public static final String LICENCE_ERROR_UNREADABLE = "licence.error.unreadable";
    public static final String LICENCE_ERROR_USER_LIMIT = "licence.error.userLimit";
    public static final String LICENCE_STATUS_EXPIRED = "licence.status.expired";
    public static final String LICENCE_STATUS_EXPIRING = "licence.status.expiring";
    public static final String LICENCE_STATUS_GRACE = "licence.status.grace";
    public static final String LICENCE_STATUS_TRIAL = "licence.status.trial";
    public static final String LICENCE_STATUS_TRIAL_OVER = "licence.status.trialOver";

    // ---- mail ------------------------------------------------------------------------------------------------
    public static final String MAIL_ENABLED = "mail.enabled";
    public static final String MAIL_ERROR_FROM_REQUIRED = "mail.error.fromRequired";
    public static final String MAIL_ERROR_HOST_REQUIRED = "mail.error.hostRequired";
    public static final String MAIL_ERROR_NO_ADDRESS = "mail.error.noAddress";
    public static final String MAIL_ERROR_NOT_ENABLED = "mail.error.notEnabled";
    public static final String MAIL_ERROR_SEND_FAILED = "mail.error.sendFailed";
    public static final String MAIL_SAVED_DISABLED = "mail.saved.disabled";
    public static final String MAIL_SAVED_ENABLED = "mail.saved.enabled";
    public static final String MAIL_TEST_BODY = "mail.test.body";
    public static final String MAIL_TEST_SENT = "mail.test.sent";
    public static final String MAIL_TEST_SUBJECT = "mail.test.subject";

    // ---- password --------------------------------------------------------------------------------------------
    public static final String PASSWORD_ERROR_CONTAINS_USERNAME = "password.error.containsUsername";
    public static final String PASSWORD_RULES = "password.rules";

    // ---- privilege -------------------------------------------------------------------------------------------
    public static final String PRIVILEGE_DELETED = "privilege.deleted";
    public static final String PRIVILEGE_ERROR_CODE_REQUIRED = "privilege.error.codeRequired";
    public static final String PRIVILEGE_ERROR_CODE_USED = "privilege.error.codeUsed";
    public static final String PRIVILEGE_ERROR_DELETE_FAILED = "privilege.error.deleteFailed";
    public static final String PRIVILEGE_ERROR_NAME_REQUIRED = "privilege.error.nameRequired";
    public static final String PRIVILEGE_ERROR_NOT_FOUND = "privilege.error.notFound";
    public static final String PRIVILEGE_SAVED = "privilege.saved";

    // ---- profile ---------------------------------------------------------------------------------------------
    public static final String PROFILE_DELETED = "profile.deleted";
    public static final String PROFILE_ERROR_DELETE_FAILED = "profile.error.deleteFailed";
    public static final String PROFILE_ERROR_LAST_MANAGE_PROFILES = "profile.error.lastManageProfiles";
    public static final String PROFILE_ERROR_NAME_REQUIRED = "profile.error.nameRequired";
    public static final String PROFILE_ERROR_NAME_USED = "profile.error.nameUsed";
    public static final String PROFILE_ERROR_NOT_FOUND = "profile.error.notFound";
    public static final String PROFILE_SAVED = "profile.saved";

    // ---- setup -----------------------------------------------------------------------------------------------
    public static final String SETUP_ERROR_ALREADY_DONE = "setup.error.alreadyDone";
    public static final String SETUP_ERROR_PASSWORD_MISMATCH = "setup.error.passwordMismatch";

    // ---- twoFactor -------------------------------------------------------------------------------------------
    public static final String TWO_FACTOR_CODES_REGENERATED = "twoFactor.codesRegenerated";
    public static final String TWO_FACTOR_DISABLED = "twoFactor.disabled";
    public static final String TWO_FACTOR_ENABLED = "twoFactor.enabled";
    public static final String TWO_FACTOR_ERROR_NOT_ENABLED = "twoFactor.error.notEnabled";
    public static final String TWO_FACTOR_ERROR_WRONG_CODE = "twoFactor.error.wrongCode";
    public static final String TWO_FACTOR_ERROR_WRONG_SETUP_CODE = "twoFactor.error.wrongSetupCode";
    public static final String TWO_FACTOR_MOVED = "twoFactor.moved";
    public static final String TWO_FACTOR_RECOVERY_CODE_USED = "twoFactor.recoveryCodeUsed";
    public static final String TWO_FACTOR_REQUIRED_FOR_PROFILE = "twoFactor.requiredForProfile";

    // ---- user ------------------------------------------------------------------------------------------------
    public static final String USER_CREATED = "user.created";
    public static final String USER_DELETED = "user.deleted";
    public static final String USER_ERROR_DELETE_FAILED = "user.error.deleteFailed";
    public static final String USER_ERROR_DELETE_SELF = "user.error.deleteSelf";
    public static final String USER_ERROR_DISABLE_SELF = "user.error.disableSelf";
    public static final String USER_ERROR_EMAIL_USED = "user.error.emailUsed";
    public static final String USER_ERROR_NOT_FOUND = "user.error.notFound";
    public static final String USER_ERROR_PROFILE_NOT_FOUND = "user.error.profileNotFound";
    public static final String USER_ERROR_PROFILE_REQUIRED = "user.error.profileRequired";
    public static final String USER_ERROR_SAME_PASSWORD = "user.error.samePassword";
    public static final String USER_ERROR_USERNAME_USED = "user.error.usernameUsed";
    public static final String USER_ERROR_WRONG_CURRENT_PASSWORD = "user.error.wrongCurrentPassword";
    public static final String USER_PASSWORD_RESET = "user.passwordReset";
    public static final String USER_TWO_FACTOR_RESET = "user.twoFactorReset";
    public static final String USER_UPDATED = "user.updated";

    // ---- keys completed in the code with a document kind (INVOICE, CREDIT_NOTE, QUOTE, PROFORMA) -------------
    public static final String INVOICE_FLASH_DRAFT_SAVED_PREFIX = "invoice.flash.draftSaved.";
    public static final String INVOICE_FLASH_ISSUED_PREFIX = "invoice.flash.issued.";
    public static final String INVOICE_VIEW_DRAFT_TITLE_PREFIX = "invoice.view.draftTitle.";
    public static final String INVOICE_FORM_TITLE_NEW_PREFIX = "invoice.form.title.new.";
    public static final String INVOICE_FORM_TITLE_EDIT_PREFIX = "invoice.form.title.edit.";
}
