package com.swiftlabs.invoicing.audit;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * The sentence shown for a page or an action in the journal, in the reader's language: "Impression de la facture
 * FA-2026-0001". Texts {@code audit.do.<Controller>.<method>} (i18n/audit), with {0} what it is about; the variant
 * ending in ".new" when it is about nothing yet (a new client saved). Views: {@code ${@auditDescriptions.of(e)}}.
 */
@Component
public class AuditDescriptions {

    private static final String PREFIX = "audit.do.";

    private final MessageSource messageSource;

    public AuditDescriptions(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** Null for a sign-in or sign-out (its type says it all); the address when the code that answered has no text. */
    public String of(AuditEvent event) {
        if (event.getHandler() == null) {
            return null;
        }
        Locale locale = LocaleContextHolder.getLocale();
        String key = key(event.getHandler());
        if (event.getSubject() == null) {
            String none = messageSource.getMessage(key + ".new", null, null, locale);
            if (none != null) {
                return none;
            }
        }
        String subject = event.getSubject() == null ? "" : event.getSubjectType() == null ? event.getSubject()
            : messageSource.getMessage("audit.doc." + event.getSubjectType(), new Object[] {event.getSubject()}, event.getSubject(), locale);
        String sentence = messageSource.getMessage(key, new Object[] {subject}, null, locale);
        return sentence != null ? sentence.strip() : event.getPath();
    }

    /** "ClientController#delete" -> "audit.do.ClientController.delete". */
    public static String key(String handler) {
        return PREFIX + handler.replace('#', '.');
    }
}
