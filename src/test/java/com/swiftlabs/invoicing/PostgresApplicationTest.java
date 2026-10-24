package com.swiftlabs.invoicing;

import com.swiftlabs.invoicing.access.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * The whole application on real PostgreSQL: every Flyway migration from an empty database, then
 * Hibernate's schema validation of every entity. Skipped when Docker is not available.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class PostgresApplicationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void runsOnPostgresqlWithEveryMigrationApplied() {
        assertThat(jdbcTemplate.queryForObject("SELECT version()", String.class)).startsWith("PostgreSQL 16");
        Integer latest = jdbcTemplate.queryForObject(
            "SELECT MAX(CAST(version AS INTEGER)) FROM flyway_schema_history WHERE success", Integer.class);
        assertThat(latest).isGreaterThanOrEqualTo(6);
        assertThat(userRepository.findByUsername("admin")).isPresent();
    }

    @Test
    void readinessIncludesTheDatabase() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(jsonPath("$.status").value("UP"));
    }
}
