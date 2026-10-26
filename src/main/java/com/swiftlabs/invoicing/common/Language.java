package com.swiftlabs.invoicing.common;

import java.util.Locale;

/**
 * The languages of the application. Each user chooses theirs (Mon compte); before login the
 * browser's language is used, French by default. Documents sent to clients (invoice PDF, emails
 * to clients) stay in French whatever the user's language.
 */
public enum Language {

    FR("fr", "Français", Locale.FRENCH),
    EN("en", "English", Locale.ENGLISH),
    // Algerian Arabic formatting: Latin digits (plain "ar" would format numbers in Arabic-Indic digits)
    AR("ar", "العربية", Locale.forLanguageTag("ar-DZ"));

    private final String code;
    private final String nativeName;
    private final Locale locale;

    Language(String code, String nativeName, Locale locale) {
        this.code = code;
        this.nativeName = nativeName;
        this.locale = locale;
    }

    /** Stored in app_user.language and used in ?lang= links: fr, en, ar. */
    public String getCode() { return code; }

    /** The language's name in itself, as offered in the language choice. */
    public String getNativeName() { return nativeName; }

    public Locale getLocale() { return locale; }

    public boolean isRightToLeft() { return this == AR; }

    /** The language of a code (fr, en, ar, case-insensitive), or empty for anything else. */
    public static java.util.Optional<Language> fromCode(String code) {
        if (code != null) {
            for (Language language : values()) {
                if (language.code.equalsIgnoreCase(code.trim())) {
                    return java.util.Optional.of(language);
                }
            }
        }
        return java.util.Optional.empty();
    }

    /** The language of a code, French when unknown. */
    public static Language of(String code) {
        return fromCode(code).orElse(FR);
    }

    /** The language of a locale (ar-DZ, en-GB, fr...), French when not supported. */
    public static Language of(Locale locale) {
        return locale == null ? FR : of(locale.getLanguage());
    }
}
