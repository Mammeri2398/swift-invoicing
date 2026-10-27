package com.swiftlabs.invoicing.backup;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Where the PostgreSQL tools connect: read from the application's own JDBC URL
 * (jdbc:postgresql://host:port/database?currentSchema=...), so the backup is always of the data in use.
 */
public record PostgresConnection(String host, int port, String database, String schema, String username, String password) {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /** Empty when the application does not run on PostgreSQL (the H2 database of the tests). */
    static Optional<PostgresConnection> parse(String jdbcUrl, String username, String password) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql://")) {
            return Optional.empty();
        }
        URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        String database = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
        if (uri.getHost() == null || database.isEmpty()) {
            return Optional.empty();
        }
        String schema = "public";
        if (uri.getRawQuery() != null) {
            for (String parameter : uri.getRawQuery().split("&")) {
                String[] pair = parameter.split("=", 2);
                if (pair.length == 2 && pair[0].equals("currentSchema")) {
                    schema = URLDecoder.decode(pair[1], StandardCharsets.UTF_8).split(",")[0];
                }
            }
        }
        // The schema goes into SQL run by psql: a plain name only
        if (!IDENTIFIER.matcher(schema).matches()) {
            return Optional.empty();
        }
        return Optional.of(new PostgresConnection(uri.getHost(), uri.getPort() > 0 ? uri.getPort() : 5432, database, schema,
            username, password == null ? "" : password));
    }

    boolean isDefaultSchema() {
        return schema.equals("public");
    }

    /** The same database, for one schema (a company's, or the shared one). */
    PostgresConnection withSchema(String otherSchema) {
        if (!IDENTIFIER.matcher(otherSchema).matches()) {
            throw new IllegalArgumentException("Not a schema name: " + otherSchema);
        }
        return new PostgresConnection(host, port, database, otherSchema, username, password);
    }
}
