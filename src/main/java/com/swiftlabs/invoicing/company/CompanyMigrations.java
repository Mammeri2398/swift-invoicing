package com.swiftlabs.invoicing.company;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Brings every company's schema to the latest db/tenant migration, after the shared migrations (db/migration).
 * A schema that already has tables but no history (the first company, moved there by V27) starts at the baseline,
 * version 1; an empty or missing schema (a new company) is created from V1.
 */
public class CompanyMigrations {

    private static final Logger log = LoggerFactory.getLogger(CompanyMigrations.class);

    private final DataSource dataSource;

    public CompanyMigrations(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void migrateAll() {
        for (String schema : schemas()) {
            migrate(schema);
        }
    }

    public void migrate(String schema) {
        int applied = Flyway.configure()
            .dataSource(dataSource)
            .schemas(CompanySchemas.checked(schema))
            .createSchemas(true)
            .locations("classpath:db/tenant")
            .baselineOnMigrate(true)
            .baselineVersion("1")
            .baselineDescription("Tables moved from the shared schema")
            .load()
            .migrate()
            .migrationsExecuted;
        if (applied > 0) {
            log.info("Company schema {}: {} migration(s) applied", schema, applied);
        }
    }

    /** The companies' schemas, read with JDBC: this runs before JPA starts. */
    private List<String> schemas() {
        List<String> schemas = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT schema_name FROM " + CompanySchemas.SHARED + ".company ORDER BY id")) {
            while (rows.next()) {
                schemas.add(rows.getString(1));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot list the companies", ex);
        }
        return schemas;
    }
}
