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

    // ---- accounting ------------------------------------------------------------------------------------------
    public static final String ACCOUNTING_ACCOUNTS_SAVED = "accounting.accounts.saved";
    public static final String ACCOUNTING_ACCOUNT_BANK = "accounting.account.bank";
    public static final String ACCOUNTING_ACCOUNT_CASH = "accounting.account.cash";
    public static final String ACCOUNTING_ACCOUNT_CUSTOMER = "accounting.account.customer";
    public static final String ACCOUNTING_ACCOUNT_SALES = "accounting.account.sales";
    public static final String ACCOUNTING_ACCOUNT_STAMP = "accounting.account.stamp";
    public static final String ACCOUNTING_ACCOUNT_VAT = "accounting.account.vat";
    public static final String ACCOUNTING_COL_ACCOUNT = "accounting.col.account";
    public static final String ACCOUNTING_COL_AMOUNT = "accounting.col.amount";
    public static final String ACCOUNTING_COL_CLIENT = "accounting.col.client";
    public static final String ACCOUNTING_COL_CREDIT = "accounting.col.credit";
    public static final String ACCOUNTING_COL_DATE = "accounting.col.date";
    public static final String ACCOUNTING_COL_DEBIT = "accounting.col.debit";
    public static final String ACCOUNTING_COL_EXCL_VAT = "accounting.col.exclVat";
    public static final String ACCOUNTING_COL_JOURNAL = "accounting.col.journal";
    public static final String ACCOUNTING_COL_KIND = "accounting.col.kind";
    public static final String ACCOUNTING_COL_LABEL = "accounting.col.label";
    public static final String ACCOUNTING_COL_METHOD = "accounting.col.method";
    public static final String ACCOUNTING_COL_NIF = "accounting.col.nif";
    public static final String ACCOUNTING_COL_NUMBER = "accounting.col.number";
    public static final String ACCOUNTING_COL_ORIGINAL = "accounting.col.original";
    public static final String ACCOUNTING_COL_PARTY = "accounting.col.party";
    public static final String ACCOUNTING_COL_PIECE = "accounting.col.piece";
    public static final String ACCOUNTING_COL_REFERENCE = "accounting.col.reference";
    public static final String ACCOUNTING_COL_STAMP = "accounting.col.stamp";
    public static final String ACCOUNTING_COL_TOTAL = "accounting.col.total";
    public static final String ACCOUNTING_COL_VAT = "accounting.col.vat";
    public static final String ACCOUNTING_EXPORT_FILE = "accounting.export.file";
    public static final String ACCOUNTING_EXPORT_SHEET_ACCOUNTS = "accounting.export.sheet.accounts";
    public static final String ACCOUNTING_EXPORT_SHEET_ENTRIES = "accounting.export.sheet.entries";
    public static final String ACCOUNTING_EXPORT_SHEET_RECEIPTS = "accounting.export.sheet.receipts";
    public static final String ACCOUNTING_EXPORT_SHEET_SALES = "accounting.export.sheet.sales";
    public static final String ACCOUNTING_JOURNAL_BANK = "accounting.journal.bank";
    public static final String ACCOUNTING_JOURNAL_CASH = "accounting.journal.cash";
    public static final String ACCOUNTING_JOURNAL_SALES = "accounting.journal.sales";
    public static final String ACCOUNTING_KIND_CANCELLATION = "accounting.kind.cancellation";
    public static final String ACCOUNTING_LABEL_CANCELLATION = "accounting.label.cancellation";
    public static final String ACCOUNTING_LABEL_CREDIT_NOTE = "accounting.label.creditNote";
    public static final String ACCOUNTING_LABEL_INVOICE = "accounting.label.invoice";
    public static final String ACCOUNTING_LABEL_PAYMENT = "accounting.label.payment";
    public static final String ACCOUNTING_LABEL_STAMP = "accounting.label.stamp";
    public static final String ACCOUNTING_LABEL_VAT = "accounting.label.vat";
    public static final String ACCOUNTING_PERIOD = "accounting.period";
    public static final String ACCOUNTING_PERIOD_CURRENT_MONTH = "accounting.period.currentMonth";
    public static final String ACCOUNTING_PERIOD_CURRENT_YEAR = "accounting.period.currentYear";
    public static final String ACCOUNTING_PERIOD_PREVIOUS_MONTH = "accounting.period.previousMonth";
    public static final String ACCOUNTING_PERIOD_PREVIOUS_QUARTER = "accounting.period.previousQuarter";
    public static final String ACCOUNTING_PERIOD_PREVIOUS_YEAR = "accounting.period.previousYear";
    public static final String ACCOUNTING_TOTAL = "accounting.total";

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
    public static final String BACKUP_ERROR_OTHER_COMPANY = "backup.error.otherCompany";
    public static final String BACKUP_ERROR_TOOL_MISSING = "backup.error.toolMissing";
    public static final String BACKUP_ERROR_UNREADABLE_BACKUP = "backup.error.unreadableBackup";
    public static final String BACKUP_IMPORTED = "backup.imported";
    public static final String BACKUP_RESTORE_WORD = "backup.restore.word";
    public static final String BACKUP_RESTORED = "backup.restored";
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

    // ---- catalog -------------------------------------------------------------------------------------------------
    public static final String CATALOG_DELETED = "catalog.deleted";
    public static final String CATALOG_ERROR_NAME_USED = "catalog.error.nameUsed";
    public static final String CATALOG_ERROR_NOT_FOUND = "catalog.error.notFound";
    public static final String CATALOG_ERROR_VAT_RATE = "catalog.error.vatRate";
    public static final String CATALOG_SAVED = "catalog.saved";

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

    // ---- company ---------------------------------------------------------------------------------------------
    public static final String COMPANY_ACTIVATED = "company.activated";
    public static final String COMPANY_CREATED = "company.created";
    public static final String COMPANY_DEACTIVATED = "company.deactivated";
    public static final String COMPANY_ERROR_DEACTIVATE_CURRENT = "company.error.deactivateCurrent";
    public static final String COMPANY_ERROR_LAST_ACTIVE = "company.error.lastActive";
    public static final String COMPANY_ERROR_NOT_FOUND = "company.error.notFound";
    public static final String COMPANY_ERROR_NAME = "company.error.name";
    public static final String COMPANY_ERROR_NO_ACCESS = "company.error.noAccess";
    public static final String COMPANY_ERROR_SWITCHED = "company.error.switched";
    public static final String COMPANY_RENAMED = "company.renamed";
    public static final String COMPANY_SWITCHED = "company.switched";

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
    public static final String INVOICE_ERROR_DATE_BEFORE_LAST = "invoice.error.dateBeforeLast";
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
    public static final String LICENCE_ERROR_COMPANY_LIMIT = "licence.error.companyLimit";
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

    // ---- recurring -------------------------------------------------------------------------------------------
    public static final String RECURRING_DELETED = "recurring.deleted";
    public static final String RECURRING_ERROR_END_BEFORE_NEXT = "recurring.error.endBeforeNext";
    public static final String RECURRING_ERROR_NOT_FOUND = "recurring.error.notFound";
    public static final String RECURRING_GENERATED = "recurring.generated";
    public static final String RECURRING_NONE_DUE = "recurring.noneDue";
    public static final String RECURRING_PAUSED = "recurring.paused";
    public static final String RECURRING_RESUMED = "recurring.resumed";
    public static final String RECURRING_SAVED = "recurring.saved";

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
    public static final String USER_ERROR_NO_COMPANY = "user.error.noCompany";
    public static final String USER_ERROR_PROFILE_NOT_FOUND = "user.error.profileNotFound";
    public static final String USER_ERROR_SAME_PASSWORD = "user.error.samePassword";
    public static final String USER_ERROR_USERNAME_USED = "user.error.usernameUsed";
    public static final String USER_ERROR_WRONG_CURRENT_PASSWORD = "user.error.wrongCurrentPassword";
    public static final String USER_PASSWORD_RESET = "user.passwordReset";
    public static final String USER_TWO_FACTOR_RESET = "user.twoFactorReset";
    public static final String USER_UPDATED = "user.updated";

    // ---- vat -------------------------------------------------------------------------------------------------
    public static final String VAT_BASIS = "vat.basis";
    public static final String VAT_COL_BASE = "vat.col.base";
    public static final String VAT_COL_OPERATION = "vat.col.operation";
    public static final String VAT_COL_VAT = "vat.col.vat";
    public static final String VAT_DETAILS_CLIENT = "vat.details.client";
    public static final String VAT_DETAILS_DATE = "vat.details.date";
    public static final String VAT_DETAILS_DOCUMENT = "vat.details.document";
    public static final String VAT_DETAILS_EXCL_VAT = "vat.details.exclVat";
    public static final String VAT_DETAILS_STAMP = "vat.details.stamp";
    public static final String VAT_DETAILS_TOTAL = "vat.details.total";
    public static final String VAT_DETAILS_VAT = "vat.details.vat";
    public static final String VAT_EXEMPT = "vat.exempt";
    public static final String VAT_EXPORT_FILE = "vat.export.file";
    public static final String VAT_EXPORT_SHEET_DETAILS = "vat.export.sheet.details";
    public static final String VAT_EXPORT_SHEET_SUMMARY = "vat.export.sheet.summary";
    public static final String VAT_RATE = "vat.rate";
    public static final String VAT_STAMP_DUTY = "vat.stampDuty";
    public static final String VAT_SUMMARY_TITLE = "vat.summary.title";
    public static final String VAT_TOTAL = "vat.total";
    public static final String VAT_TOTAL_INCL_VAT = "vat.totalInclVat";
    /** Completed with the basis: VAT_BASIS_PREFIX + VatBasis.INVOICED.name(). */
    public static final String VAT_BASIS_PREFIX = "vat.basis.";

    // ---- keys completed in the code with a document kind (INVOICE, CREDIT_NOTE, QUOTE, PROFORMA) -------------
    public static final String INVOICE_FLASH_DRAFT_SAVED_PREFIX = "invoice.flash.draftSaved.";
    public static final String INVOICE_FLASH_ISSUED_PREFIX = "invoice.flash.issued.";
    public static final String INVOICE_VIEW_DRAFT_TITLE_PREFIX = "invoice.view.draftTitle.";
    public static final String INVOICE_FORM_TITLE_NEW_PREFIX = "invoice.form.title.new.";
    public static final String INVOICE_FORM_TITLE_EDIT_PREFIX = "invoice.form.title.edit.";
}
