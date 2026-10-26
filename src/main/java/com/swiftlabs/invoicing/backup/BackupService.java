package com.swiftlabs.invoicing.backup;

import com.swiftlabs.invoicing.common.BusinessException;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Backups of the database into a folder of the PC: automatic every day (see BackupScheduler), on
 * demand, and before every restore. Only one backup or restore runs at a time.
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    /** Every backup file starts with this: written by pg_dump in custom format. */
    private static final byte[] MAGIC = "PGDMP".getBytes();

    private final BackupProperties properties;
    private final BackupSettingsRepository settingsRepository;
    private final PostgresTools tools;
    private final BackupDatabase database;
    private final ObjectProvider<Flyway> flyway;
    private final DataSource dataSource;
    private final Clock clock;
    private final ReentrantLock running = new ReentrantLock();

    private volatile Failure lastFailure;

    public BackupService(BackupProperties properties, BackupSettingsRepository settingsRepository, PostgresTools tools,
                         BackupDatabase database, ObjectProvider<Flyway> flyway, DataSource dataSource, Clock clock) {
        this.properties = properties;
        this.settingsRepository = settingsRepository;
        this.tools = tools;
        this.database = database;
        this.flyway = flyway;
        this.dataSource = dataSource;
        this.clock = clock;
    }

    /** The last failed backup, cleared by the next one that works. */
    public record Failure(LocalDateTime at, String message) {
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    /** Enabled and running on PostgreSQL (the tools cannot back up another database). */
    public boolean isAvailable() {
        return properties.enabled() && database.connection().isPresent();
    }

    public Failure lastFailure() {
        return lastFailure;
    }

    @Transactional(readOnly = true)
    public BackupSettings settings() {
        return settingsRepository.findById(BackupSettings.ID)
            .orElseGet(() -> new BackupSettings(properties.directory(), properties.keep()));
    }

    /** The backup folder, absolute (relative folders are from the application's folder). */
    @Transactional(readOnly = true)
    public Path directory() {
        return Path.of(settings().getDirectory()).toAbsolutePath().normalize();
    }

    @Transactional
    public void saveSettings(String directory, int keepCount) {
        if (keepCount < 1 || keepCount > 365) {
            throw new BusinessException("keepCount", "Entre 1 et 365 sauvegardes.");
        }
        String trimmed = directory == null ? "" : directory.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException("directory", "Indiquez le dossier des sauvegardes.");
        }
        Path folder;
        try {
            folder = Path.of(trimmed).toAbsolutePath().normalize();
            Files.createDirectories(folder);
        } catch (InvalidPathException | IOException ex) {
            throw new BusinessException("directory", "Ce dossier ne peut pas être créé : " + trimmed);
        }
        if (!Files.isWritable(folder)) {
            throw new BusinessException("directory", "L'application ne peut pas écrire dans ce dossier.");
        }
        BackupSettings settings = settingsRepository.findById(BackupSettings.ID)
            .orElseGet(() -> new BackupSettings(trimmed, keepCount));
        settings.setDirectory(trimmed);
        settings.setKeepCount(keepCount);
        settingsRepository.save(settings);
        log.info("Backup settings changed: folder {}, {} automatic backups kept", folder, keepCount);
    }

    /** The backups of the folder, newest first. */
    public List<BackupFile> list() {
        Path folder = directory();
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        List<BackupFile> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*" + BackupFile.EXTENSION)) {
            for (Path file : stream) {
                BackupFile.of(file, Files.size(file)).ifPresent(files::add);
            }
        } catch (IOException ex) {
            log.warn("Cannot list the backups of {}: {}", folder, ex.getMessage());
            return List.of();
        }
        files.sort(Comparator.comparing(BackupFile::createdAt).reversed());
        return files;
    }

    /** The newest backup of any kind, if any. */
    public Optional<BackupFile> latest() {
        return list().stream().findFirst();
    }

    /** True when the newest automatic backup is old enough for the next one (or there is none). */
    public boolean isAutomaticBackupDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        // An hour of slack: the scheduler checks every hour
        return list().stream().filter(file -> file.kind() == BackupKind.AUTO).findFirst()
            .map(file -> !file.createdAt().plus(properties.every()).minusHours(1).isAfter(now))
            .orElse(true);
    }

    /** Makes a backup now; automatic ones then delete the oldest automatic backups beyond the number kept. */
    public BackupFile backup(BackupKind kind) {
        if (!running.tryLock()) {
            throw new BusinessException("Une sauvegarde ou une restauration est déjà en cours, réessayez dans un instant.");
        }
        try {
            BackupFile file = backupLocked(kind);
            if (kind == BackupKind.AUTO) {
                deleteOldAutomaticBackups();
            }
            return file;
        } finally {
            running.unlock();
        }
    }

    private BackupFile backupLocked(BackupKind kind) {
        PostgresConnection target = requireAvailable();
        try {
            Path folder = createdDirectory();
            LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
            String name = BackupFile.nameFor(now, kind);
            Path file = folder.resolve(name);
            Path partial = folder.resolve(name + ".part");
            try {
                tools.dump(target, partial);
                // A backup the tools cannot read back is no backup
                tools.contents(partial);
                move(partial, file);
            } finally {
                PostgresTools.deleteQuietly(partial);
            }
            BackupFile backup = BackupFile.of(file, Files.size(file)).orElseThrow();
            lastFailure = null;
            log.info("Backup made: {} ({})", file, backup.displaySize());
            return backup;
        } catch (BusinessException ex) {
            lastFailure = new Failure(LocalDateTime.now(clock), ex.getMessage());
            log.error("Backup failed: {}", ex.getMessage());
            throw ex;
        } catch (IOException ex) {
            lastFailure = new Failure(LocalDateTime.now(clock), "Le dossier des sauvegardes n'est pas accessible : " + ex.getMessage());
            log.error("Backup failed", ex);
            throw new BusinessException(lastFailure.message());
        }
    }

    private void deleteOldAutomaticBackups() {
        int keep = settings().getKeepCount();
        List<BackupFile> automatic = list().stream().filter(file -> file.kind() == BackupKind.AUTO).toList();
        for (BackupFile old : automatic.subList(Math.min(keep, automatic.size()), automatic.size())) {
            PostgresTools.deleteQuietly(directory().resolve(old.name()));
            log.info("Old automatic backup deleted: {}", old.name());
        }
    }

    /** The file of a backup of the folder, for download or restore. */
    public Path file(String name) {
        if (!BackupFile.isValidName(name)) {
            throw new BusinessException("Sauvegarde introuvable.");
        }
        Path file = directory().resolve(name);
        if (!Files.isRegularFile(file)) {
            throw new BusinessException("Sauvegarde introuvable.");
        }
        return file;
    }

    public void delete(String name) {
        Path file = file(name);
        PostgresTools.deleteQuietly(file);
        log.info("Backup deleted: {}", file);
    }

    /** Adds a backup file from another PC (or an older copy) to the folder, ready to be restored. */
    public BackupFile importFile(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new BusinessException("Choisissez un fichier de sauvegarde (.dump).");
        }
        requireAvailable();
        try (InputStream in = upload.getInputStream()) {
            if (!Arrays.equals(in.readNBytes(MAGIC.length), MAGIC)) {
                throw new BusinessException("Ce fichier n'est pas une sauvegarde de Swift Invoicing.");
            }
        } catch (IOException ex) {
            throw new BusinessException("Le fichier n'a pas pu être lu.");
        }
        try {
            Path folder = createdDirectory();
            String name = BackupFile.nameFor(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS), BackupKind.IMPORTED);
            Path partial = folder.resolve(name + ".part");
            Path file = folder.resolve(name);
            try {
                upload.transferTo(partial);
                checkContents(partial);
                move(partial, file);
            } finally {
                PostgresTools.deleteQuietly(partial);
            }
            log.info("Backup imported: {} (from {})", file, upload.getOriginalFilename());
            return BackupFile.of(file, Files.size(file)).orElseThrow();
        } catch (IOException ex) {
            throw new BusinessException("Le fichier n'a pas pu être enregistré dans le dossier des sauvegardes.");
        }
    }

    /**
     * Replaces all the data with the backup. A backup of the current data is made first, so a restore
     * can always be undone. Afterwards the schema is brought up to date (a backup of an older version)
     * and the database connections are renewed.
     */
    public BackupFile restore(String name) {
        PostgresConnection target = requireAvailable();
        Path file = file(name);
        if (!running.tryLock()) {
            throw new BusinessException("Une sauvegarde ou une restauration est déjà en cours, réessayez dans un instant.");
        }
        try {
            checkContents(file);
            BackupFile safety = backupLocked(BackupKind.BEFORE_RESTORE);
            log.warn("Restoring the backup {} (the data before it is in {})", name, safety.name());
            tools.restore(target, file, directory());
            flyway.ifAvailable(Flyway::migrate);
            if (dataSource instanceof HikariDataSource hikari && hikari.getHikariPoolMXBean() != null) {
                hikari.getHikariPoolMXBean().softEvictConnections();
            }
            log.warn("Backup {} restored", name);
            return safety;
        } finally {
            running.unlock();
        }
    }

    /** Refuses files that are readable backups but not of this application (or of another schema). */
    private void checkContents(Path file) {
        String schema = database.connection().map(PostgresConnection::schema).orElse("public");
        List<String> contents = tools.contents(file);
        boolean ours = contents.stream().anyMatch(line -> line.contains(" TABLE " + schema + " flyway_schema_history "))
            && contents.stream().anyMatch(line -> line.contains(" TABLE " + schema + " invoice "));
        if (!ours) {
            throw new BusinessException("Ce fichier n'est pas une sauvegarde de Swift Invoicing.");
        }
    }

    private PostgresConnection requireAvailable() {
        if (!properties.enabled()) {
            throw new BusinessException("Les sauvegardes ne sont pas activées sur cette installation.");
        }
        return database.connection().orElseThrow(() -> new BusinessException("Les sauvegardes demandent une base PostgreSQL."));
    }

    private Path createdDirectory() throws IOException {
        Path folder = directory();
        try {
            Files.createDirectories(folder);
        } catch (AccessDeniedException ex) {
            throw new BusinessException("Le dossier des sauvegardes n'est pas accessible en écriture : " + folder);
        }
        return folder;
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
