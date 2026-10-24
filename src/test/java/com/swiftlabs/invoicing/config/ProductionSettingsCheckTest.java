package com.swiftlabs.invoicing.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSettingsCheckTest {

    /** A complete production configuration. */
    private static MockEnvironment production() {
        return new MockEnvironment()
            .withProperty("spring.datasource.url", "jdbc:postgresql://db:5432/invoicing")
            .withProperty("spring.datasource.username", "invoicing")
            .withProperty("spring.datasource.password", "s3cret")
            .withProperty("app.base-url", "https://factures.example.dz")
            .withProperty("mail.enabled", "true");
    }

    private static void check(MockEnvironment environment) {
        ProductionSettingsCheck check = new ProductionSettingsCheck();
        check.setEnvironment(environment);
        check.postProcessBeanFactory(null);
    }

    @Test
    void acceptsProductionSettings() {
        assertThat(ProductionSettingsCheck.problems(production())).isEmpty();
        assertThatCode(() -> check(production())).doesNotThrowAnyException();
    }

    @Test
    void insecureCookieAndPlainHttpOnlyWarn() {
        MockEnvironment environment = production()
            .withProperty("server.servlet.session.cookie.secure", "false")
            .withProperty("app.base-url", "http://factures.example.dz");
        assertThatCode(() -> check(environment)).doesNotThrowAnyException();
    }

    @Test
    void reportsMissingVariablesByName() {
        // Same shape as application.properties: the value refers to a variable that is not set
        MockEnvironment environment = production().withProperty("spring.datasource.password", "${DB_PASSWORD}");
        assertThat(ProductionSettingsCheck.problems(environment))
            .containsExactly("DB_PASSWORD is not set (spring.datasource.password)");

        MockEnvironment empty = production().withProperty("app.base-url", " ");
        assertThat(ProductionSettingsCheck.problems(empty)).anyMatch(p -> p.startsWith("APP_BASE_URL is not set"));
    }

    @Test
    void refusesDevelopmentSettings() {
        assertThat(ProductionSettingsCheck.problems(production().withProperty("mail.enabled", "false")))
            .anyMatch(p -> p.startsWith("MAIL_ENABLED=false"));
        assertThat(ProductionSettingsCheck.problems(production().withProperty("app.base-url", "http://localhost:7070")))
            .anyMatch(p -> p.startsWith("APP_BASE_URL=http://localhost:7070"));
        assertThat(ProductionSettingsCheck.problems(production().withProperty("spring.thymeleaf.cache", "false")))
            .anyMatch(p -> p.startsWith("spring.thymeleaf.cache=false"));
    }

    @Test
    void reportsEveryProblemAtOnce() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.thymeleaf.cache", "false");
        assertThatThrownBy(() -> check(environment))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DB_URL").hasMessageContaining("DB_USER").hasMessageContaining("DB_PASSWORD")
            .hasMessageContaining("APP_BASE_URL").hasMessageContaining("MAIL_ENABLED").hasMessageContaining("thymeleaf");
    }
}
