package com.swiftlabs.invoicing.company;

import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/** One schema per company: the migrations of every company at start-up, and Hibernate's connections per company. */
@Configuration
public class MultiCompanyConfig {

    @Bean
    public CompanyMigrations companyMigrations(DataSource dataSource,
                                               @Value("${app.companies.default-schema:}") String defaultSchema) {
        // Where work without a company goes: nowhere in the application, the first company in the tests
        CompanyContext.setFallback(defaultSchema);
        return new CompanyMigrations(dataSource);
    }

    /** The shared migrations, then each company's, before JPA starts (and validates the tables). */
    @Bean
    public FlywayMigrationStrategy companyAwareMigration(CompanyMigrations companyMigrations) {
        return flyway -> {
            flyway.migrate();
            companyMigrations.migrateAll();
        };
    }

    @Bean
    public HibernatePropertiesCustomizer companySchemaCustomizer(DataSource dataSource) {
        return properties -> {
            properties.put(MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER, new CompanySchemaConnections(dataSource));
            properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, new CurrentCompanyResolver());
        };
    }
}
