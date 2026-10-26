package com.swiftlabs.invoicing.backup;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** The database the backups are made of: the application's own datasource, when it is PostgreSQL. */
@Component
public class BackupDatabase {

    private final Optional<PostgresConnection> connection;

    public BackupDatabase(Environment environment) {
        this.connection = PostgresConnection.parse(environment.getProperty("spring.datasource.url"),
            environment.getProperty("spring.datasource.username"), environment.getProperty("spring.datasource.password"));
    }

    public Optional<PostgresConnection> connection() {
        return connection;
    }
}
