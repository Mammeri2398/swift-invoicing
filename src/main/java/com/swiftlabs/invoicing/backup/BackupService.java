package com.swiftlabs.invoicing.backup;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.TimedCache;
import com.swiftlabs.invoicing.company.CompanyContext;
import com.swiftlabs.invoicing.company.CompanyMigrations;
import com.swiftlabs.invoicing.issuer.IssuerService;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Backups of the database into a folder of the PC: automatic every day (see BackupScheduler), on
 * demand, and before every restore. Only one backup or restore runs at a time.
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    /** Every backup file starts with this: written by pg_dump in custom format. */
    private static final byte[] MAGIC = "PGDMP".getBytes();
    /** A company's invoice table in the table of contents of a backup ("TABLE company_1 invoice"). */
    /** One entry of "pg_restore --list": "215; 1259 16390 TABLE company_1 invoice owner" (group 1 from its type on). */
    private static final Pattern CONTENTS_ENTRY = Pattern.compile("\\d+; \\d+ \\d+ (.*)");
    private static final Pattern ALLOWED_ENTRY = Pattern.compile(
        "(TABLE DATA|TABLE|SEQUENCE SET|SEQUENCE OWNED BY|SEQUENCE|DEFAULT|FK CONSTRAINT|CONSTRAINT|INDEX|SCHEMA) ");
    private static final Pattern COMPANY_INVOICES = Pattern.compile(" TABLE company_[0-9]+ invoice ");

    private final BackupProperties properties;
    private final BackupSettingsRepository settingsRepository;
    private final PostgresTools tools;
    private final BackupDatabase database;
    private final CompanyMigrations companyMigrations;
    private final DataSource dataSource;
    private final Clock clock;
    private final ReentrantLock running = new ReentrantLock();

    /** The last failure of each company's backups (and of the shared data's), by owner. */
    private final Map<String, Failure> lastFailures = new ConcurrentHashMap<>();
    /** The newest backup of each company, for the warning shown on every page of who manages backups (a folder listing). */
    private final TimedCache<String, Optional<BackupFile>> latest;
    private final IssuerService issuerService;

    public BackupService(BackupProperties properties, BackupSettingsRepository settingsRepository, PostgresTools tools,
                         BackupDatabase database, CompanyMigrations companyMigrations,
                         DataSource dataSource, Clock clock, IssuerService issuerService,
                         @Value("${app.page-cache.ttl:60s}") Duration cacheTtl) {
        this.issuerService = issuerService;
        this.latest = new TimedCache<>(cacheTtl);
        this.properties = properties;
        this.settingsRepository = settingsRepository;
        this.tools = tools;
        this.database = database;
        this.companyMigrations = companyMigrations;
        this.dataSource = dataSource;
        this.clock = clock;
    }

    /**
     * The last failed backup, cleared by the next one that works. The error is kept (message key and
     * arguments) so that each user reads it in their language; {@link #message()} is the French text.
     */
    public record Failure(LocalDateTime at, BusinessException error) {

        public String message() {
            return error.getMessage();
        }
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    /** Enabled and running on PostgreSQL (the tools cannot back up another database). */
    public boolean isAvailable() {
        return properties.enabled() && database.connection().isPresent();
    }

    /** The last failure of the current company's backups, cleared by the next one that works. */
    public Failure lastFailure() {
        return lastFailures.get(owner());
    }

    /**
     * Whose backups: the current company's (its schema). Backups always belong to a company; the data shared by
     * the companies has its own backups ({@link #backupShared}).
     */
    private static String owner() {
        String schema = CompanyContext.currentSchema();
        if (CompanyContext.currentCompanyId() == null) {
            throw new IllegalStateException("Backups belong to a company: none is set (" + schema + ")");
        }
        return schema;
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
        latest.clear();
        if (keepCount < 1 || keepCount > 365) {
            throw BusinessException.onField("keepCount", MessageProperties.BACKUP_ERROR_KEEP_COUNT);
        }
        String trimmed = directory == null ? "" : directory.trim();
        if (trimmed.isEmpty()) {
            throw BusinessException.onField("directory", MessageProperties.BACKUP_ERROR_DIRECTORY_REQUIRED);
        }
        Path folder;
        try {
            folder = Path.of(trimmed).toAbsolutePath().normalize();
            Files.createDirectories(folder);
        } catch (InvalidPathException | IOException ex) {
            throw BusinessException.onField("directory", MessageProperties.BACKUP_ERROR_DIRECTORY_CREATE, trimmed);
        }
        if (!Files.isWritable(folder)) {
            throw BusinessException.onField("directory", MessageProperties.BACKUP_ERROR_DIRECTORY_NOT_WRITABLE);
        }
        BackupSettings settings = settingsRepository.findById(BackupSettings.ID)
            .orElseGet(() -> new BackupSettings(trimmed, keepCount));
        settings.setDirectory(trimmed);
        settings.setKeepCount(keepCount);
        settingsRepository.save(settings);
        log.info("Backup settings changed: folder {}, {} automatic backups kept", folder, keepCount);
    }

    /** The current company's backups of the folder, newest first. */
    public List<BackupFile> list() {
        return list(owner());
    }

    /** The backups of the data shared by the companies (users, profiles, licence), newest first. */
    public List<BackupFile> listShared() {
        return list(BackupFile.SHARED);
    }

    private List<BackupFile> list(String owner) {
        Path folder = folderOf(owner);
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        List<BackupFile> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*" + BackupFile.EXTENSION)) {
            for (Path file : stream) {
                BackupFile.of(file, Files.size(file)).filter(backup -> backup.owner().equals(owner)).ifPresent(files::add);
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
        return latest.get(owner(), () -> list().stream().findFirst());
    }

    /** True when the current company's newest automatic backup is old enough for the next one (or there is none). */
    public boolean isAutomaticBackupDue() {
        return isDue(list());
    }

    /** The same for the shared data. */
    public boolean isSharedBackupDue() {
        return isDue(listShared());
    }

    private boolean isDue(List<BackupFile> backups) {
        LocalDateTime now = LocalDateTime.now(clock);
        // An hour of slack: the scheduler checks every hour
        return backups.stream().filter(file -> file.kind() == BackupKind.AUTO).findFirst()
            .map(file -> !file.createdAt().plus(properties.every()).minusHours(1).isAfter(now))
            .orElse(true);
    }

    /** Makes a backup now; automatic ones then delete the oldest automatic backups beyond the number kept. */
    public BackupFile backup(BackupKind kind) {
        latest.clear();
        if (!running.tryLock()) {
            throw new BusinessException(MessageProperties.BACKUP_ERROR_BUSY);
        }
        try {
            BackupFile file = backupLocked(owner(), kind);
            if (kind == BackupKind.AUTO) {
                deleteOldAutomaticBackups(directory(), list(), settings().getKeepCount());
            }
            return file;
        } finally {
            running.unlock();
        }
    }

    /**
     * Backs up the data shared by the companies: users, profiles, privileges, licence, the companies and their
     * accesses. Kept in the default backup folder, as many as the configured number of automatic backups. Restored
     * by hand only (pg_restore): a restore page would let one company's administrator change everyone's access.
     */
    public BackupFile backupShared() {
        if (!running.tryLock()) {
            throw new BusinessException(MessageProperties.BACKUP_ERROR_BUSY);
        }
        try {
            BackupFile file = backupLocked(BackupFile.SHARED, BackupKind.AUTO);
            deleteOldAutomaticBackups(sharedDirectory(), listShared(), properties.keep());
            return file;
        } finally {
            running.unlock();
        }
    }

    private BackupFile backupLocked(String owner, BackupKind kind) {
        PostgresConnection target = requireAvailable().withSchema(BackupFile.SHARED.equals(owner) ? "public" : owner);
        try {
            Path folder = created(folderOf(owner));
            LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
            String name = BackupFile.nameFor(owner, now, kind);
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
            lastFailures.remove(owner);
            log.info("Backup made: {} ({})", file, backup.displaySize());
            return backup;
        } catch (BusinessException ex) {
            lastFailures.put(owner, new Failure(LocalDateTime.now(clock), ex));
            log.error("Backup of {} failed: {}", owner, ex.getMessage());
            throw ex;
        } catch (IOException ex) {
            BusinessException error = new BusinessException(MessageProperties.BACKUP_ERROR_FOLDER_INACCESSIBLE, String.valueOf(ex.getMessage()));
            lastFailures.put(owner, new Failure(LocalDateTime.now(clock), error));
            log.error("Backup of {} failed", owner, ex);
            throw error;
        }
    }

    private void deleteOldAutomaticBackups(Path folder, List<BackupFile> backups, int keep) {
        List<BackupFile> automatic = backups.stream().filter(file -> file.kind() == BackupKind.AUTO).toList();
        for (BackupFile old : automatic.subList(Math.min(keep, automatic.size()), automatic.size())) {
            PostgresTools.deleteQuietly(folder.resolve(old.name()));
            log.info("Old automatic backup deleted: {}", old.name());
        }
    }

    /** The file of one of the current company's backups, for download or restore. */
    public Path file(String name) {
        return file(name, owner());
    }

    /** The file of a backup of the shared data, for download. */
    public Path sharedFile(String name) {
        return file(name, BackupFile.SHARED);
    }

    private Path file(String name, String owner) {
        if (!BackupFile.isValidName(name)) {
            throw new BusinessException(MessageProperties.BACKUP_ERROR_NOT_FOUND);
        }
        Path file = folderOf(owner).resolve(name);
        if (!Files.isRegularFile(file) || BackupFile.of(file, 0).filter(backup -> backup.owner().equals(owner)).isEmpty()) {
            throw new BusinessException(MessageProperties.BACKUP_ERROR_NOT_FOUND);
        }
        return file;
    }

    public void delete(String name) {
        latest.clear();
        Path file = file(name);
        PostgresTools.deleteQuietly(file);
        log.info("Backup deleted: {}", file);
    }

    /** Adds a backup file from another PC (or an older copy) to the folder, ready to be restored. */
    public BackupFile importFile(MultipartFile upload) {
        latest.clear();
        if (upload == null || upload.isEmpty()) {
            throw new BusinessException(MessageProperties.BACKUP_ERROR_CHOOSE_FILE);
        }
        requireAvailable();
        try (InputStream in = upload.getInputStream()) {
            if (!Arrays.equals(in.readNBytes(MAGIC.length), MAGIC)) {
                throw new BusinessException(MessageProperties.BACKUP_ERROR_NOT_OURS);
            }
        } catch (IOException ex) {
            throw new BusinessException(MessageProperties.BACKUP_ERROR_FILE_UNREADABLE);
        }
        try {
            Path folder = created(directory());
            String name = BackupFile.nameFor(owner(), LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS), BackupKind.IMPORTED);
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
            throw new BusinessException(MessageProperties.BACKUP_ERROR_FILE_NOT_SAVED);
        }
    }

    /**
     * Replaces the current company's data with the backup; the other companies and the shared data are not
     * touched. A backup of the company's current data is made first, so a restore can always be undone.
     * Afterwards its schema is brought up to date (a backup of an older version) and the database connections are
     * renewed.
     */
    public BackupFile restore(String name) {
        latest.clear();
        // The restored company's own details (legal status, TVA mention) are read again
        issuerService.forgetCachedProfiles();
        String owner = owner();
        PostgresConnection target = requireAvailable().withSchema(owner);
        Path file = file(name);
        if (!running.tryLock()) {
            throw new BusinessException(MessageProperties.BACKUP_ERROR_BUSY);
        }
        try {
            checkContents(file);
            BackupFile safety = backupLocked(owner, BackupKind.BEFORE_RESTORE);
            log.warn("Restoring the backup {} of {} (the data before it is in {})", name, owner, safety.name());
            tools.restore(target, file, directory());
            companyMigrations.migrate(owner);
            if (dataSource instanceof HikariDataSource hikari && hikari.getHikariPoolMXBean() != null) {
                hikari.getHikariPoolMXBean().softEvictConnections();
            }
            log.warn("Backup {} restored", name);
            return safety;
        } finally {
            running.unlock();
        }
    }

    /**
     * Refuses files that are readable backups but not of the current company: another company's, a backup of the
     * shared data, or one made before the companies had their own schema.
     */
    private void checkContents(Path file) {
        String schema = owner();
        List<String> contents = tools.contents(file);
        // Only what a company's backup holds, in its schema: no views, functions, triggers...
        for (String line : contents) {
            Matcher entry = CONTENTS_ENTRY.matcher(line);
            if (entry.matches() && line.contains(" " + schema + " ") && !ALLOWED_ENTRY.matcher(entry.group(1)).lookingAt()) {
                throw new BusinessException(MessageProperties.BACKUP_ERROR_UNSAFE);
            }
        }
        if (contents.stream().noneMatch(line -> line.contains(" TABLE " + schema + " invoice "))) {
            throw new BusinessException(contents.stream().anyMatch(line -> COMPANY_INVOICES.matcher(line).find()
                    || line.contains(" TABLE public invoice "))
                ? MessageProperties.BACKUP_ERROR_OTHER_COMPANY : MessageProperties.BACKUP_ERROR_NOT_OURS);
        }
    }

    private PostgresConnection requireAvailable() {
        if (!properties.enabled()) {
            throw new BusinessException(MessageProperties.BACKUP_ERROR_NOT_ENABLED);
        }
        return database.connection().orElseThrow(() -> new BusinessException(MessageProperties.BACKUP_ERROR_NEEDS_POSTGRES));
    }

    /**
     * The folder of the shared data's backups: the installation's (app.backup.directory), whatever folder a company
     * chose for its own; read without a company.
     */
    private Path sharedDirectory() {
        return Path.of(properties.directory()).toAbsolutePath().normalize();
    }

    private Path folderOf(String owner) {
        return BackupFile.SHARED.equals(owner) ? sharedDirectory() : directory();
    }

    private static Path created(Path folder) throws IOException {
        try {
            Files.createDirectories(folder);
        } catch (AccessDeniedException ex) {
            throw new BusinessException(MessageProperties.BACKUP_ERROR_FOLDER_NOT_WRITABLE, folder.toString());
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
