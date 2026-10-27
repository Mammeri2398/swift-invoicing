package com.swiftlabs.invoicing.common;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * The application's texts, in messages*.properties and i18n/*.properties (one file per language:
 * area.properties in French, area_en and area_ar). Every text is a java.text.MessageFormat pattern:
 * an apostrophe is written '' and {0}, {1}... are the arguments.
 *
 * In Java: messages.get("client.saved", name) in the current user's language. In templates:
 * #{client.saved(${name})}, and ${@messages.label(status)} for an enum (key enum.Type.CONSTANT).
 */
@Component("messages")
public class Messages {

    /** Every bundle of texts; add the bundle of a new area here. */
    public static final String[] BASENAMES = {
        "messages",
        "i18n/common",
        "i18n/account",
        "i18n/access",
        "i18n/setup",
        "i18n/invoice",
        "i18n/invoice-pages",
        "i18n/client",
        "i18n/catalog",
        "i18n/dashboard",
        "i18n/settings",
        "i18n/help",
    };

    private static volatile MessageSource french;

    private final MessageSource messageSource;

    public Messages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** The bundles, configured the same way for the application and for {@link #french}. */
    public static ResourceBundleMessageSource newMessageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames(BASENAMES);
        source.setDefaultEncoding("UTF-8");
        // French is the base file of each bundle: never the language of the PC
        source.setFallbackToSystemLocale(false);
        source.setDefaultLocale(Locale.FRENCH);
        source.setAlwaysUseMessageFormat(true);
        return source;
    }

    /** French text, without Spring: exception messages in logs and tests, documents for clients. */
    public static String french(String code, Object... args) {
        if (french == null) {
            french = newMessageSource();
        }
        return french.getMessage(code, args, code, Locale.FRENCH);
    }

    /** The text in the current request's language. */
    public String get(String code, Object... args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }

    /** The text in a given language (an email to another user, in their language). */
    public String get(Language language, String code, Object... args) {
        return messageSource.getMessage(code, args, language.getLocale());
    }

    /** The message of a business error, in the current request's language. */
    public String of(BusinessException ex) {
        return messageSource.getMessage(ex.getCode(), ex.getArgs(), ex.getMessage(), LocaleContextHolder.getLocale());
    }

    /** The label of an enum constant: key enum.SimpleName.CONSTANT (e.g. enum.InvoiceStatus.PAID). */
    public String label(Enum<?> value) {
        if (value == null) {
            return "";
        }
        return get("enum." + value.getDeclaringClass().getSimpleName() + "." + value.name());
    }

    /** The current request's language. */
    public Language current() {
        return Language.of(LocaleContextHolder.getLocale());
    }
}
