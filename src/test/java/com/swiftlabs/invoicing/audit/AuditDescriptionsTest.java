package com.swiftlabs.invoicing.audit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Locale;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/** Every page and action of the application reads as a sentence in the journal, in each language. */
@SpringBootTest
class AuditDescriptionsTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private MessageSource messageSource;

    @Test
    void everyPageAndActionHasItsSentence() {
        TreeSet<String> missing = new TreeSet<>();
        handlerMapping.getHandlerMethods().values().stream()
            .filter(method -> method.getBeanType().getPackageName().startsWith("com.swiftlabs"))
            .map(method -> AuditDescriptions.key(method.getBeanType().getSimpleName() + "#" + method.getMethod().getName()))
            .forEach(key -> {
                for (Locale locale : new Locale[] {Locale.FRENCH, Locale.ENGLISH, Locale.forLanguageTag("ar")}) {
                    if (messageSource.getMessage(key, null, null, locale) == null) {
                        missing.add(key + " (" + locale + ")");
                    }
                }
            });
        assertThat(missing).isEmpty();
    }
}
