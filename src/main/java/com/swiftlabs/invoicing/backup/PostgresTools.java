package com.swiftlabs.invoicing.backup;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.MessageProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Runs the PostgreSQL tools that come with the database: pg_dump makes a backup (custom format,
 * compressed), pg_restore reads it, psql loads it back. The password goes through PGPASSWORD,
 * never on the command line.
 */
@Component
public class PostgresTools {

    private static final Logger log = LoggerFactory.getLogger(PostgresTools.class);

    private final BackupProperties properties;

    public PostgresTools(BackupProperties properties) {
        this.properties = properties;
    }

    /** What was being done when a tool fails (backup.tool.*): resolved in the reader's language. */
    private static DefaultMessageSourceResolvable step(String code) {
        return new DefaultMessageSourceResolvable(new String[] {code}, null, (String) null);
    }

    /** Writes a backup of one schema (a company's, or the shared data) to {@code target}. */
    public void dump(PostgresConnection connection, Path target) {
        List<String> command = command("pg_dump", connection);
        command.addAll(List.of("--format=custom", "--file=" + target, "--schema=" + connection.schema()));
        deleteQuietly(run(command, connection, target.getParent(), step(MessageProperties.BACKUP_TOOL_DUMP)));
    }

    /** The table of contents of a backup, one entry per line; fails when the file is not a readable backup. */
    public List<String> contents(Path backup) {
        Path output = run(new ArrayList<>(List.of(tool("pg_restore"), "--list", backup.toString())), null,
            backup.getParent(), step(MessageProperties.BACKUP_TOOL_LIST));
        try {
            return Files.readAllLines(output, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new BusinessException(MessageProperties.BACKUP_ERROR_UNREADABLE_BACKUP);
        } finally {
            deleteQuietly(output);
        }
    }

    /**
     * Replaces the application's data with the backup, in one transaction: every table of the schema is
     * dropped first (tables added by a later version included, Flyway brings the schema up to date
     * afterwards), then the backup is loaded. Any error rolls everything back.
     */
    public void restore(PostgresConnection connection, Path backup, Path workDirectory) {
        Path script = workDirectory.resolve("restauration-" + System.nanoTime() + ".sql");
        Path restoreScript = workDirectory.resolve("restauration-" + System.nanoTime() + "-complete.sql");
        try {
            // Only the schema restored: a backup of the whole database (made before the companies had their own
            // backups) gives back that company alone
            deleteQuietly(run(new ArrayList<>(List.of(tool("pg_restore"), "--clean", "--if-exists", "--no-owner", "--no-privileges",
                "--schema=" + connection.schema(), "--file=" + script, backup.toString())), null, workDirectory,
                step(MessageProperties.BACKUP_TOOL_READ)));
            writeRestoreScript(connection.schema(), script, restoreScript);
            List<String> command = command("psql", connection);
            command.addAll(List.of("--no-psqlrc", "--quiet", "--single-transaction", "--set=ON_ERROR_STOP=1",
                "--file=" + restoreScript));
            deleteQuietly(run(command, connection, workDirectory, step(MessageProperties.BACKUP_TOOL_RESTORE)));
        } finally {
            deleteQuietly(script);
            deleteQuietly(restoreScript);
        }
    }

    private static final Pattern PSQL_LOCK = Pattern.compile("\\\\(un)?restrict [A-Za-z0-9]+");
    private static final Pattern COPY_FROM_STDIN = Pattern.compile("COPY .* FROM stdin;");
    private static final Pattern COMPANY_SCHEMA = Pattern.compile("\\bcompany_\\d+\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORBIDDEN = Pattern.compile(String.join("|",
        "\\bpublic\"?\\s*\\.", "search_path", "\\bset\\s+role\\b", "session\\s+authorization",
        "\\bcreate\\s+(or\\s+replace\\s+)?(function|procedure|trigger|rule|view|materialized|extension|event|policy|schema|role|user)\\b",
        "\\bdo\\s+\\$", "\\bcall\\b", "\\bgrant\\b", "\\brevoke\\b", "\\balter\\s+(role|user|system|schema|default|database|function)\\b",
        "\\bcopy\\b.*\\b(program|to)\\b", "\\bdblink", "\\blo_(import|export)\\b", "\\bpg_(read|ls|stat)_", "\\bexecute\\b"),
        Pattern.CASE_INSENSITIVE);
    /** The one search_path statement of a backup's header (an empty path: every name is written with its schema). */
    private static final String HEADER_SEARCH_PATH = "SELECT pg_catalog.set_config('search_path', '', false);";

    /**
     * A statement of a restore script is only what a backup of one company holds: its tables, sequences,
     * constraints and indexes, named in its own schema. Anything else (another company's or the shared schema,
     * functions, roles, a different search path) could reach beyond the company, as the script runs with the
     * application's own database account: the whole restore is refused.
     */
    static boolean isSafeStatement(String schema, String line) {
        if (line.equals(HEADER_SEARCH_PATH) || line.isBlank() || line.startsWith("--")) {
            return true;
        }
        // psql commands (\! runs a program, \i reads a file...): only those recent tools write to lock the script
        if (line.stripLeading().startsWith("\\")) {
            return PSQL_LOCK.matcher(line).matches();
        }
        Matcher companies = COMPANY_SCHEMA.matcher(line);
        while (companies.find()) {
            if (!companies.group().equalsIgnoreCase(schema)) {
                return false;
            }
        }
        // pg_catalog.setval of the company's own sequences restores them
        String withoutSetval = line.startsWith("SELECT pg_catalog.setval('" + schema + ".") ? "" : line;
        return !FORBIDDEN.matcher(withoutSetval).find();
    }

    private void writeRestoreScript(String schema, Path script, Path restoreScript) {
        try (BufferedReader reader = Files.newBufferedReader(script, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(restoreScript, StandardCharsets.UTF_8)) {
            writer.write("""
                DO $$
                DECLARE r record;
                BEGIN
                  FOR r IN SELECT tablename FROM pg_tables WHERE schemaname = '%1$s' LOOP
                    EXECUTE format('DROP TABLE IF EXISTS %%I.%%I CASCADE', '%1$s', r.tablename);
                  END LOOP;
                  FOR r IN SELECT sequencename FROM pg_sequences WHERE schemaname = '%1$s' LOOP
                    EXECUTE format('DROP SEQUENCE IF EXISTS %%I.%%I CASCADE', '%1$s', r.sequencename);
                  END LOOP;
                END $$;
                """.formatted(schema));
            String line;
            int lineNumber = 0;
            boolean data = false;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                // Written by recent tools in the header, unknown to a PostgreSQL server older than 17
                if (lineNumber < 50 && line.equals("SET transaction_timeout = 0;")) {
                    continue;
                }
                // The rows of a table (COPY ... FROM stdin; up to \.) are data, not statements
                if (data) {
                    data = !line.equals("\\.");
                } else if (COPY_FROM_STDIN.matcher(line).matches()) {
                    data = true;
                } else if (!isSafeStatement(schema, line)) {
                    log.warn("Restore refused: unexpected statement in the backup, line {}: {}", lineNumber,
                        line.length() > 200 ? line.substring(0, 200) : line);
                    throw new BusinessException(MessageProperties.BACKUP_ERROR_UNSAFE);
                }
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException ex) {
            throw new BusinessException(MessageProperties.BACKUP_ERROR_BACKUP_NOT_READ, String.valueOf(ex.getMessage()));
        }
    }

    private List<String> command(String tool, PostgresConnection connection) {
        return new ArrayList<>(List.of(tool(tool), "--host=" + connection.host(), "--port=" + connection.port(),
            "--username=" + connection.username(), "--dbname=" + connection.database(), "--no-password"));
    }

    private String tool(String name) {
        String directory = properties.toolsDirectory();
        if (directory == null || directory.isBlank()) {
            return name;
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        return Path.of(directory).resolve(windows ? name + ".exe" : name).toString();
    }

    /** Runs the command; its output (and errors) go to a file, returned on success. */
    private Path run(List<String> command, PostgresConnection connection, Path workDirectory, DefaultMessageSourceResolvable failure) {
        Path output;
        try {
            output = Files.createTempFile(workDirectory, "pg-", ".log");
        } catch (IOException ex) {
            throw new BusinessException(MessageProperties.BACKUP_ERROR_FOLDER_NOT_WRITABLE, workDirectory.toString());
        }
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
        builder.environment().put("PGCONNECT_TIMEOUT", "10");
        if (connection != null) {
            builder.environment().put("PGPASSWORD", connection.password());
        }
        Process process;
        try {
            process = builder.start();
        } catch (IOException ex) {
            deleteQuietly(output);
            log.error("Cannot run {}: {}", command.get(0), ex.getMessage());
            throw new BusinessException(MessageProperties.BACKUP_ERROR_TOOL_MISSING, Path.of(command.get(0)).getFileName().toString());
        }
        try {
            if (!process.waitFor(properties.timeout().toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                deleteQuietly(output);
                throw new BusinessException(MessageProperties.BACKUP_TOOL_TIMEOUT, failure);
            }
        } catch (InterruptedException ex) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            deleteQuietly(output);
            throw new BusinessException(MessageProperties.BACKUP_TOOL_INTERRUPTED, failure);
        }
        if (process.exitValue() != 0) {
            String detail = lastLines(output);
            deleteQuietly(output);
            log.error("{} failed (exit {}): {}", Path.of(command.get(0)).getFileName(), process.exitValue(), detail);
            throw detail.isEmpty() ? new BusinessException(MessageProperties.BACKUP_TOOL_FAILED, failure)
                : new BusinessException(MessageProperties.BACKUP_TOOL_FAILED_DETAIL, failure, detail);
        }
        return output;
    }

    private static String lastLines(Path output) {
        try {
            List<String> lines = Files.readAllLines(output, StandardCharsets.UTF_8).stream().filter(line -> !line.isBlank()).toList();
            return String.join(" ", lines.subList(Math.max(0, lines.size() - 3), lines.size())).trim();
        } catch (IOException ex) {
            return "";
        }
    }

    static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            log.warn("Cannot delete {}: {}", file, ex.getMessage());
        }
    }
}
