package com.swiftlabs.invoicing.company;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Database connections for Hibernate, pointed at a company: its schema first, then the shared one. Unqualified tables
 * (the company's) resolve to the company; the shared tables are named with their schema on their entities. A
 * connection goes back to the pool pointed at the shared schema only, so a connection used without a company can
 * never reach a company's tables.
 */
class CompanySchemaConnections implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;
    private volatile Boolean postgres;

    CompanySchemaConnections(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Used by Hibernate outside any session (schema validation at start-up): the first company's tables. */
    @Override
    public Connection getAnyConnection() throws SQLException {
        return getConnection(CompanySchemas.FIRST);
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        releaseConnection(CompanySchemas.FIRST, connection);
    }

    @Override
    public Connection getConnection(String schema) throws SQLException {
        Connection connection = dataSource.getConnection();
        try {
            pointTo(connection, CompanySchemas.checked(schema));
            return connection;
        } catch (SQLException | RuntimeException ex) {
            connection.close();
            throw ex;
        }
    }

    @Override
    public void releaseConnection(String schema, Connection connection) throws SQLException {
        try {
            pointTo(connection, null);
        } finally {
            connection.close();
        }
    }

    /** {@code schema} first then the shared schema; {@code null}: the shared schema only. */
    private void pointTo(Connection connection, String schema) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (isPostgres(connection)) {
                statement.execute(schema != null ? "SET search_path TO " + schema + ", public" : "SET search_path TO public");
            } else {
                // H2 (tests, with DATABASE_TO_LOWER as PostgreSQL)
                statement.execute(schema != null ? "SET SCHEMA " + schema : "SET SCHEMA public");
                statement.execute(schema != null ? "SET SCHEMA_SEARCH_PATH " + schema + ", public" : "SET SCHEMA_SEARCH_PATH public");
            }
        }
    }

    private boolean isPostgres(Connection connection) throws SQLException {
        if (postgres == null) {
            postgres = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
        }
        return postgres;
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return unwrapType.isInstance(this);
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        if (unwrapType.isInstance(this)) {
            return unwrapType.cast(this);
        }
        throw new IllegalArgumentException("Cannot unwrap to " + unwrapType);
    }
}
