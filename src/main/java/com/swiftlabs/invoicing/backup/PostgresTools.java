package com.swiftlabs.invoicing.backup;

import com.swiftlabs.invoicing.common.BusinessException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    /** Writes a backup of the application's data to {@code target}. */
    public void dump(PostgresConnection connection, Path target) {
        List<String> command = command("pg_dump", connection);
        command.addAll(List.of("--format=custom", "--file=" + target));
        if (!connection.isDefaultSchema()) {
            command.add("--schema=" + connection.schema());
        }
        deleteQuietly(run(command, connection, target.getParent(), "La sauvegarde a échoué"));
    }

    /** The table of contents of a backup, one entry per line; fails when the file is not a readable backup. */
    public List<String> contents(Path backup) {
        Path output = run(new ArrayList<>(List.of(tool("pg_restore"), "--list", backup.toString())), null,
            backup.getParent(), "Ce fichier n'est pas une sauvegarde lisible");
        try {
            return Files.readAllLines(output, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new BusinessException("Ce fichier n'est pas une sauvegarde lisible.");
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
            deleteQuietly(run(new ArrayList<>(List.of(tool("pg_restore"), "--clean", "--if-exists", "--no-owner", "--no-privileges",
                "--file=" + script, backup.toString())), null, workDirectory, "La sauvegarde n'a pas pu être lue"));
            writeRestoreScript(connection.schema(), script, restoreScript);
            List<String> command = command("psql", connection);
            command.addAll(List.of("--no-psqlrc", "--quiet", "--single-transaction", "--set=ON_ERROR_STOP=1",
                "--file=" + restoreScript));
            deleteQuietly(run(command, connection, workDirectory, "La restauration a échoué, aucune donnée n'a été modifiée"));
        } finally {
            deleteQuietly(script);
            deleteQuietly(restoreScript);
        }
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
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                // Written by recent tools in the header, unknown to a PostgreSQL server older than 17
                if (lineNumber < 50 && line.equals("SET transaction_timeout = 0;")) {
                    continue;
                }
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException ex) {
            throw new BusinessException("La sauvegarde n'a pas pu être lue : " + ex.getMessage());
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
    private Path run(List<String> command, PostgresConnection connection, Path workDirectory, String failure) {
        Path output;
        try {
            output = Files.createTempFile(workDirectory, "pg-", ".log");
        } catch (IOException ex) {
            throw new BusinessException("Le dossier des sauvegardes n'est pas accessible en écriture : " + workDirectory);
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
            throw new BusinessException("Outil PostgreSQL introuvable (" + Path.of(command.get(0)).getFileName()
                + ") : vérifiez le réglage app.backup.tools-directory.");
        }
        try {
            if (!process.waitFor(properties.timeout().toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                deleteQuietly(output);
                throw new BusinessException(failure + " : délai dépassé.");
            }
        } catch (InterruptedException ex) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            deleteQuietly(output);
            throw new BusinessException(failure + " : interrompue.");
        }
        if (process.exitValue() != 0) {
            String detail = lastLines(output);
            deleteQuietly(output);
            log.error("{} failed (exit {}): {}", Path.of(command.get(0)).getFileName(), process.exitValue(), detail);
            throw new BusinessException(failure + (detail.isEmpty() ? "." : " : " + detail));
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
