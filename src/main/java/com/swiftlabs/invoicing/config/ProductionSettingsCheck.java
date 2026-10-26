package com.swiftlabs.invoicing.config;

import com.swiftlabs.invoicing.common.MessageProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertyResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Refuses to start the "prod" profile with missing or development-only settings.
 *
 * <p>Runs as a bean factory post-processor, i.e. before any bean is created: a missing variable
 * is reported by name instead of failing later (Spring keeps an unresolved "${DB_PASSWORD}" as
 * the literal password, and the database then rejects it with an unrelated error).
 */
@Component
@Profile("prod")
public class ProductionSettingsCheck implements BeanFactoryPostProcessor, EnvironmentAware {

    private static final Logger log = LoggerFactory.getLogger(ProductionSettingsCheck.class);

    /** Property -> environment variable that provides it. */
    private static final Map<String, String> REQUIRED = new LinkedHashMap<>();

    static {
        REQUIRED.put("spring.datasource.url", "DB_URL");
        REQUIRED.put("spring.datasource.username", "DB_USER");
        REQUIRED.put("spring.datasource.password", "DB_PASSWORD");
        REQUIRED.put("app.base-url", "APP_BASE_URL");
    }

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        List<String> problems = problems(environment);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Unsafe settings for the prod profile:\n - " + String.join("\n - ", problems));
        }
        if (!environment.getProperty("server.servlet.session.cookie.secure", Boolean.class, true)) {
            log.warn("SESSION_COOKIE_SECURE=false: the session cookie is also sent over plain HTTP. Serve the application over HTTPS.");
        }
        String baseUrl = environment.getProperty("app.base-url", "");
        if (!baseUrl.startsWith("https://")) {
            log.warn("APP_BASE_URL={} is not HTTPS: password reset links will travel unencrypted.", baseUrl);
        }
    }

    /** Every blocking problem, so they can all be fixed at once. */
    static List<String> problems(PropertyResolver environment) {
        List<String> problems = new ArrayList<>();
        REQUIRED.forEach((property, variable) -> {
            if (!isSet(environment, property)) {
                problems.add(variable + " is not set (" + property + ")");
            }
        });
        if (!environment.getProperty(MessageProperties.MAIL_ENABLED, Boolean.class, false)) {
            problems.add("MAIL_ENABLED=false: password reset links would be written to the log");
        }
        if (isSet(environment, "app.base-url")) {
            String baseUrl = environment.getProperty("app.base-url");
            if (baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1")) {
                problems.add("APP_BASE_URL=" + baseUrl + ": password reset links would point to localhost");
            }
        }
        if (!environment.getProperty("spring.thymeleaf.cache", Boolean.class, true)) {
            problems.add("spring.thymeleaf.cache=false: templates would be re-parsed on every request");
        }
        return problems;
    }

    /** Present, not blank, and not an unresolved ${...} placeholder. */
    private static boolean isSet(PropertyResolver environment, String property) {
        try {
            String value = environment.getProperty(property);
            return value != null && !value.isBlank() && !value.contains("${");
        } catch (IllegalArgumentException unresolvablePlaceholder) {
            return false;
        }
    }
}
