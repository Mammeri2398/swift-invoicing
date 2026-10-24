package com.swiftlabs.invoicing.access.user;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs V3 against data that existed before it: users without a profile. */
class UserProfileMigrationTest {

    private static final String URL = "jdbc:h2:mem:v3migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration").target(target).load();
    }

    @Test
    void usersWithoutProfileAreMovedToProfileWithoutPrivileges() throws Exception {
        flyway("2").migrate();
        try (Connection c = DriverManager.getConnection(URL, "sa", "");
             Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO app_user (username, email, password) VALUES ('orphan', 'orphan@example.test', 'x')");

            flyway("3").migrate();

            try (ResultSet rs = st.executeQuery(
                    "SELECT p.name, (SELECT COUNT(*) FROM profile_privilege pp WHERE pp.profile_id = p.id) "
                        + "FROM app_user u JOIN profile p ON p.id = u.profile_id WHERE u.username = 'orphan'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("Sans accès");
                assertThat(rs.getLong(2)).isZero();
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'APP_USER' AND COLUMN_NAME = 'PROFILE_ID'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("NO");
            }
        }
    }
}
