package com.swiftlabs.invoicing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real PostgreSQL for tests, started in Docker. {@code @ServiceConnection} points the datasource
 * at the container (instead of the H2 database of the other tests). Use it with
 * {@code @Import(PostgresTestcontainersConfiguration.class)} and
 * {@code @Testcontainers(disabledWithoutDocker = true)}, so the test is skipped where Docker is unavailable.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfiguration {

    /** Same major version as docker-compose.yml. */
    private static final String IMAGE = "postgres:16-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(IMAGE);
    }
}
