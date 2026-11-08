package com.swiftlabs.invoicing.backup;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.company.CompanySchemas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Backups with the PostgreSQL tools replaced by a fake (the tests run on H2): what is kept, named,
 * refused and shown. The tools themselves were checked by hand against PostgreSQL.
 */
@SpringBootTest(properties = "app.backup.enabled=true")
@AutoConfigureMockMvc
@Transactional
class BackupTest {

    private static final Path FOLDER;

    static {
        try {
            FOLDER = Files.createTempDirectory("swift-backups");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** What pg_restore --list prints for a backup of the first company. */
    private static final List<String> OUR_CONTENTS = List.of(
        "215; 1259 16390 TABLE company_1 flyway_schema_history invoicing",
        "220; 1259 16400 TABLE company_1 invoice invoicing");

    @DynamicPropertySource
    static void folder(DynamicPropertyRegistry registry) {
        registry.add("app.backup.directory", FOLDER::toString);
    }

    @MockitoBean
    private PostgresTools tools;

    @MockitoBean
    private BackupDatabase database;

    @Autowired
    private BackupService backupService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final PostgresConnection connection = new PostgresConnection("localhost", 5432, "invoicing", "public", "invoicing", "secret");

    @BeforeEach
    void fakeTools() throws IOException {
        try (Stream<Path> files = Files.list(FOLDER)) {
            for (Path file : files.toList()) {
                Files.delete(file);
            }
        }
        when(database.connection()).thenReturn(Optional.of(connection));
        doAnswer(call -> Files.writeString(call.getArgument(1), "PGDMP fake backup")).when(tools).dump(any(), any());
        when(tools.contents(any())).thenReturn(OUR_CONTENTS);
    }

    private Path existing(LocalDateTime createdAt, BackupKind kind) throws IOException {
        return Files.writeString(FOLDER.resolve(BackupFile.nameFor(CompanySchemas.FIRST, createdAt, kind)), "PGDMP old");
    }

    @Test
    void aBackupIsAFileNamedByDateAndKind() {
        BackupFile backup = backupService.backup(BackupKind.MANUAL);

        assertThat(backup.name()).matches("swift-invoicing-\\d{4}-\\d{2}-\\d{2}-\\d{6}-manuelle\\.dump");
        assertThat(FOLDER.resolve(backup.name())).hasContent("PGDMP fake backup");
        assertThat(backupService.list()).containsExactly(backup);
        // Written aside, then renamed: never a half-written backup in the list
        verify(tools).dump(eq(connection.withSchema(CompanySchemas.FIRST)), eq(FOLDER.resolve(backup.name() + ".part")));
    }

    @Test
    void onlyTheNewestAutomaticBackupsAreKept() throws IOException {
        jdbc.update("INSERT INTO " + CompanySchemas.FIRST + ".backup_settings (id, directory, keep_count) VALUES (1, ?, 3)", FOLDER.toString());
        LocalDateTime now = LocalDateTime.now();
        for (int day = 1; day <= 5; day++) {
            existing(now.minusDays(day), BackupKind.AUTO);
        }
        Path manual = existing(now.minusDays(40), BackupKind.MANUAL);

        backupService.backup(BackupKind.AUTO);

        assertThat(backupService.list()).filteredOn(file -> file.kind() == BackupKind.AUTO).hasSize(3);
        assertThat(backupService.list().get(1).createdAt().toLocalDate()).isEqualTo(now.minusDays(1).toLocalDate());
        assertThat(manual).exists();
    }

    @Test
    void automaticBackupIsDueOncePerDay() throws IOException {
        assertThat(backupService.isAutomaticBackupDue()).isTrue();
        Path recent = existing(LocalDateTime.now().minusHours(5), BackupKind.AUTO);
        assertThat(backupService.isAutomaticBackupDue()).isFalse();
        Files.delete(recent);
        existing(LocalDateTime.now().minusHours(23).minusMinutes(5), BackupKind.AUTO);
        assertThat(backupService.isAutomaticBackupDue()).isTrue();
    }

    @Test
    void aFailedBackupIsShownUntilOneWorks() throws Exception {
        doThrow(new BusinessException("La sauvegarde a échoué : connexion refusée")).when(tools).dump(any(), any());
        assertThatThrownBy(() -> backupService.backup(BackupKind.AUTO)).isInstanceOf(BusinessException.class);
        assertThat(backupService.list()).isEmpty();

        mockMvc.perform(get("/clients").with(admin()))
            .andExpect(content().string(containsString("Problème de sauvegarde : La sauvegarde a échoué : connexion refusée")));

        doAnswer(call -> Files.writeString(call.getArgument(1), "PGDMP")).when(tools).dump(any(), any());
        backupService.backup(BackupKind.MANUAL);
        assertThat(backupService.lastFailure()).isNull();
        mockMvc.perform(get("/clients").with(admin()))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Problème de sauvegarde"))));
    }

    @Test
    void anOldBackupIsWarnedAbout() throws Exception {
        existing(LocalDateTime.now().minusDays(BackupAdvice.STALE_DAYS + 1), BackupKind.AUTO);
        mockMvc.perform(get("/clients").with(admin()))
            .andExpect(content().string(containsString("Aucune sauvegarde depuis plus de 3 jours.")));
        mockMvc.perform(get("/clients").with(admin()).header("Accept-Language", "en"))
            .andExpect(content().string(containsString("No backup for more than 3 days.")));
    }

    @Test
    void namesOutsideTheFolderAreRefused() {
        assertThatThrownBy(() -> backupService.file("../../etc/passwd")).hasMessage("Sauvegarde introuvable.");
        assertThatThrownBy(() -> backupService.file("swift-invoicing-2026-01-01-000000-auto.dump")).hasMessage("Sauvegarde introuvable.");
    }

    @Test
    @WithMockUser(authorities = "VIEW_ISSUER")
    void theBackupPageNeedsItsPrivilege() throws Exception {
        mockMvc.perform(get("/settings/backups")).andExpect(status().isForbidden());
    }

    @Test
    void thePageListsBackupsAndDownloadsThem() throws Exception {
        Path file = existing(LocalDateTime.of(2026, 10, 25, 14, 30, 5), BackupKind.AUTO);

        mockMvc.perform(get("/settings/backups").with(admin()))
            .andExpect(content().string(containsString("25/10/2026 à 14:30")))
            .andExpect(content().string(containsString("Automatique")))
            .andExpect(content().string(containsString("Sauvegarder maintenant")));
        mockMvc.perform(get("/settings/backups/" + file.getFileName()).with(admin()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("swift-invoicing-2026-10-25-143005-auto.dump")))
            .andExpect(content().string("PGDMP old"));
    }

    @Test
    void restoringNeedsTheConfirmationWord() throws Exception {
        Path file = existing(LocalDateTime.now().minusDays(1), BackupKind.AUTO);

        mockMvc.perform(post("/settings/backups/" + file.getFileName() + "/restore").param("confirmation", "oui").with(admin()).with(csrf()))
            .andExpect(redirectedUrl("/settings/backups"))
            .andExpect(flash().attribute("error", "Restauration annulée : tapez RESTAURER pour confirmer."));
        mockMvc.perform(post("/settings/backups/" + file.getFileName() + "/restore").param("confirmation", "no").with(admin()).with(csrf())
                .header("Accept-Language", "en"))
            .andExpect(flash().attribute("error", "Restore cancelled: type RESTORE to confirm."));
        verify(tools, never()).restore(any(), any(), any());
    }

    @Test
    void restoringKeepsTheCurrentDataAsideAndTouchesOnlyThisCompany() throws Exception {
        Path file = existing(LocalDateTime.now().minusDays(1), BackupKind.AUTO);

        mockMvc.perform(post("/settings/backups/" + file.getFileName() + "/restore").param("confirmation", "restaurer").with(admin()).with(csrf()))
            .andExpect(redirectedUrl("/settings/backups"))
            .andExpect(flash().attribute("success", containsString("restaurée pour cette société")));

        // The company's schema alone, after a backup of its current data
        verify(tools).restore(connection.withSchema(CompanySchemas.FIRST), file, FOLDER);
        verify(tools).dump(eq(connection.withSchema(CompanySchemas.FIRST)), any());
        assertThat(backupService.list()).extracting(BackupFile::kind).contains(BackupKind.BEFORE_RESTORE);
    }

    @Test
    void anotherCompanysBackupIsNeitherListedNorRestored() throws Exception {
        Path theirs = Files.writeString(FOLDER.resolve(BackupFile.nameFor("company_2", LocalDateTime.now().minusDays(1), BackupKind.AUTO)),
            "PGDMP theirs");
        Path mine = existing(LocalDateTime.now().minusDays(2), BackupKind.AUTO);
        assertThat(theirs.getFileName().toString()).startsWith("swift-invoicing-company_2-");
        assertThat(mine.getFileName().toString()).doesNotContain("company_");

        // A shared folder: each company sees its own backups only
        assertThat(backupService.list()).extracting(BackupFile::name).containsExactly(mine.getFileName().toString());
        mockMvc.perform(post("/settings/backups/" + theirs.getFileName() + "/restore").param("confirmation", "RESTAURER").with(admin()).with(csrf()))
            .andExpect(flash().attribute("error", "Sauvegarde introuvable."));

        // Imported from elsewhere: refused when it holds another company's data
        when(tools.contents(any())).thenReturn(List.of("220; 1259 16400 TABLE company_2 invoice invoicing"));
        mockMvc.perform(multipart("/settings/backups/import")
                .file(new MockMultipartFile("file", "autre.dump", "application/octet-stream", "PGDMP other".getBytes()))
                .with(admin()).with(csrf()))
            .andExpect(flash().attribute("error", containsString("n'est pas celle de cette société")));
        verify(tools, never()).restore(any(), any(), any());
    }

    @Test
    void theDataSharedByTheCompaniesHasItsOwnBackups() {
        BackupFile shared = backupService.backupShared();

        assertThat(shared.name()).startsWith("swift-invoicing-commun-");
        verify(tools).dump(eq(connection.withSchema("public")), any());
        assertThat(backupService.listShared()).extracting(BackupFile::name).containsExactly(shared.name());
        assertThat(backupService.list()).isEmpty();
        assertThat(backupService.isSharedBackupDue()).isFalse();
    }

    @Test
    void aFileOfAnotherApplicationIsNotRestored() throws Exception {
        Path file = existing(LocalDateTime.now().minusDays(1), BackupKind.AUTO);
        when(tools.contents(file)).thenReturn(List.of("215; 1259 16390 TABLE public orders shop"));

        mockMvc.perform(post("/settings/backups/" + file.getFileName() + "/restore").param("confirmation", "RESTAURER").with(admin()).with(csrf()))
            .andExpect(flash().attribute("error", "Ce fichier n'est pas une sauvegarde de Swift Invoicing."));
        verify(tools, never()).restore(any(), any(), any());
    }

    @Test
    void anImportedBackupJoinsTheList() throws Exception {
        mockMvc.perform(multipart("/settings/backups/import")
                .file(new MockMultipartFile("file", "copie.dump", "application/octet-stream", "PGDMP copy".getBytes()))
                .with(admin()).with(csrf()))
            .andExpect(redirectedUrl("/settings/backups"));
        assertThat(backupService.list()).extracting(BackupFile::kind).containsExactly(BackupKind.IMPORTED);

        mockMvc.perform(multipart("/settings/backups/import")
                .file(new MockMultipartFile("file", "facture.pdf", "application/pdf", "%PDF-1.7".getBytes()))
                .with(admin()).with(csrf()))
            .andExpect(flash().attribute("error", "Ce fichier n'est pas une sauvegarde de Swift Invoicing."));
        assertThat(backupService.list()).hasSize(1);
    }

    @Test
    void theFolderAndRetentionCanBeChanged() throws Exception {
        Path other = FOLDER.resolve("externe");
        mockMvc.perform(post("/settings/backups/settings").param("directory", other.toString()).param("keepCount", "10")
                .with(admin()).with(csrf()))
            .andExpect(flash().attribute("success", "Réglages des sauvegardes enregistrés."));
        assertThat(backupService.directory()).isEqualTo(other);
        assertThat(backupService.settings().getKeepCount()).isEqualTo(10);

        mockMvc.perform(post("/settings/backups/settings").param("directory", other.toString()).param("keepCount", "0")
                .with(admin()).with(csrf()))
            .andExpect(flash().attribute("error", "Entre 1 et 365 sauvegardes."));
    }

    @Test
    void administratorsGetTheNewPrivilege() {
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM profile_privilege pp JOIN privilege p ON p.id = pp.privilege_id
            WHERE pp.profile_id = 1 AND p.code = 'MANAGE_BACKUPS'""", Integer.class)).isEqualTo(1);
    }

    @Test
    void theConnectionIsReadFromTheJdbcUrl() {
        assertThat(PostgresConnection.parse("jdbc:postgresql://localhost:5433/invoicing", "u", "p"))
            .contains(new PostgresConnection("localhost", 5433, "invoicing", "public", "u", "p"));
        assertThat(PostgresConnection.parse("jdbc:postgresql://db/invoicing?currentSchema=demo&ssl=false", "u", null))
            .contains(new PostgresConnection("db", 5432, "invoicing", "demo", "u", ""));
        assertThat(PostgresConnection.parse("jdbc:postgresql://db/invoicing?currentSchema=x;drop", "u", "p")).isEmpty();
        assertThat(PostgresConnection.parse("jdbc:h2:mem:test", "sa", "")).isEmpty();
    }

    @Test
    void aRealCompanyRestoreScriptIsAccepted() throws IOException {
        // pg_restore --clean --if-exists --no-owner --no-privileges --schema=company_1 of a company's tables (no rows)
        try (var script = getClass().getResourceAsStream("/backup/company-schema-restore.sql")) {
            List<String> lines = new String(script.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList();
            assertThat(lines).hasSizeGreaterThan(100);
            PostgresTools.RestoreScriptCheck check = new PostgresTools.RestoreScriptCheck(CompanySchemas.FIRST);
            lines.forEach(check::accept);
            check.finish();
        }
    }

    @Test
    void rowsAreOnlyAcceptedForTheCompanysOwnTables() {
        PostgresTools.RestoreScriptCheck check = new PostgresTools.RestoreScriptCheck(CompanySchemas.FIRST);
        for (String line : List.of("COPY company_1.client (id, name, \"position\") FROM stdin;", "1\tACME\t1",
                "-- not a statement: rows", "\\.")) {
            check.accept(line);
        }
        check.finish();
        for (String line : List.of(
                "COPY public.user_company (user_id, company_id, profile_id) FROM stdin;",
                "COPY company_2.client (id) FROM stdin;",
                "COPY company_1.client (id) FROM stdin; DELETE FROM public.app_user; COPY company_1.client (id) FROM stdin;",
                "COPY app_user (id, password) FROM stdin;")) {
            assertThatThrownBy(() -> new PostgresTools.RestoreScriptCheck(CompanySchemas.FIRST).accept(line))
                .as(line).isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void aRestoreScriptThatReachesBeyondItsCompanyIsRefused() {
        for (String line : List.of(
                "CREATE VIEW company_1.client AS SELECT * FROM company_2.client;",
                "ALTER TABLE ONLY company_1.invoice ADD CONSTRAINT x FOREIGN KEY (a) REFERENCES company_2.invoice(id);",
                "DELETE FROM public.app_user;",
                "UPDATE \"public\".app_user SET password = 'x';",
                "SET search_path = company_2;",
                "SELECT pg_catalog.set_config('search_path', 'company_2', false);",
                "CREATE FUNCTION company_1.f() RETURNS void AS $$ BEGIN END $$ LANGUAGE plpgsql;",
                "CREATE TRIGGER t AFTER INSERT ON company_1.client FOR EACH ROW EXECUTE FUNCTION company_1.f();",
                "DO $$ BEGIN PERFORM 1; END $$;",
                "SET ROLE postgres;",
                "COPY company_1.client TO PROGRAM 'rm -rf /';",
                "\\! rm -rf /",
                "\\i /etc/passwd",
                "SELECT pg_catalog.setval('company_1.client_id_seq', 3, true); DELETE FROM public.app_user;",
                "INSERT INTO U&\"\\0070ublic\".app_user (id) VALUES (1);",
                "INSERT INTO app_user (id) VALUES (1);",
                "CREATE TABLE company_1.x (id int); DELETE FROM app_user;",
                "ALTER TABLE company_1.x ADD COLUMN y int DEFAULT $q$a$q$;",
                "CREATE\nFUNCTION company_1.f() RETURNS void AS $$ BEGIN END $$ LANGUAGE sql;",
                "UPDATE public\n.app_user SET password = 'x';",
                "UPDATE public/**/.app_user SET password = 'x';")) {
            assertThat(PostgresTools.isSafeStatement(CompanySchemas.FIRST, line)).as(line).isFalse();
        }
        for (String line : List.of(
                "SELECT pg_catalog.setval('company_1.client_id_seq', 3, true);",
                "ALTER TABLE ONLY company_1.invoice_line ADD CONSTRAINT f FOREIGN KEY (invoice_id) REFERENCES company_1.invoice(id) ON DELETE CASCADE;",
                "\\restrict 98Esbp8LHRub")) {
            assertThat(PostgresTools.isSafeStatement(CompanySchemas.FIRST, line)).as(line).isTrue();
        }
    }

    @Test
    void aBackupWithViewsOrFunctionsInTheCompanyIsRefused() throws Exception {
        when(tools.contents(any())).thenReturn(List.of(
            "220; 1259 16400 TABLE company_1 invoice invoicing",
            "230; 1259 16500 VIEW company_1 client invoicing"));
        mockMvc.perform(multipart("/settings/backups/import")
                .file(new MockMultipartFile("file", "piege.dump", "application/octet-stream", "PGDMP trap".getBytes()))
                .with(admin()).with(csrf()))
            .andExpect(flash().attribute("error", containsString("ne concernent pas cette société")));
        assertThat(backupService.list()).isEmpty();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin")
            .authorities(() -> "MANAGE_BACKUPS", () -> "VIEW_CLIENTS");
    }
}
