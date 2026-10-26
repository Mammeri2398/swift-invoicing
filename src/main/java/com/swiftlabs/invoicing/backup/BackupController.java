package com.swiftlabs.invoicing.backup;

import com.swiftlabs.invoicing.access.auth.SessionService;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.Language;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.common.Privileges;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Path;

/** Réglages > Sauvegardes: backups of the database, download, import and restore. */
@Controller
@RequestMapping(Endpoints.BACKUPS)
@PreAuthorize(Privileges.Has.MANAGE_BACKUPS)
public class BackupController {

    /**
     * Typed by the user to confirm a restore (it replaces all the data): RESTAURER, or the word of the
     * user's language (backup.restore.word).
     */
    static final String RESTORE_CONFIRMATION = "RESTAURER";

    private final BackupService backupService;
    private final SessionService sessionService;
    private final Messages messages;

    public BackupController(BackupService backupService, SessionService sessionService, Messages messages) {
        this.backupService = backupService;
        this.sessionService = sessionService;
        this.messages = messages;
    }

    @GetMapping
    public String page(Model model) {
        BackupSettings settings = backupService.settings();
        model.addAttribute(CommonCodes.Attributes.ENABLED, backupService.isEnabled());
        model.addAttribute(CommonCodes.Attributes.AVAILABLE, backupService.isAvailable());
        model.addAttribute(CommonCodes.Attributes.BACKUPS, backupService.list());
        model.addAttribute(CommonCodes.Attributes.DIRECTORY, backupService.directory().toString());
        model.addAttribute(CommonCodes.Attributes.SETTINGS, settings);
        model.addAttribute(CommonCodes.Attributes.FAILURE, backupService.lastFailure());
        model.addAttribute(CommonCodes.Attributes.CONFIRMATION, messages.get(MessageProperties.BACKUP_RESTORE_WORD));
        return CommonCodes.Views.BACKUP_BACKUPS;
    }

    @PostMapping(Endpoints.BACKUP_NOW)
    public String backupNow(RedirectAttributes redirectAttributes) {
        try {
            BackupFile backup = backupService.backup(BackupKind.MANUAL);
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.BACKUP_DONE, backup.name(),
                size(backup)));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
        }
        return Endpoints.REDIRECT + Endpoints.BACKUPS;
    }

    @PostMapping(Endpoints.BACKUP_SETTINGS)
    public String saveSettings(@RequestParam String directory, @RequestParam(defaultValue = "0") int keepCount,
                               RedirectAttributes redirectAttributes) {
        try {
            backupService.saveSettings(directory, keepCount);
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.BACKUP_SETTINGS_SAVED));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
        }
        return Endpoints.REDIRECT + Endpoints.BACKUPS;
    }

    @GetMapping(Endpoints.BACKUP_FILE)
    public ResponseEntity<Resource> download(@PathVariable String name) {
        Path file = backupService.file(name);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(new FileSystemResource(file));
    }

    @PostMapping(Endpoints.BACKUP_IMPORT)
    public String importFile(@RequestParam(name = "file", required = false) MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            BackupFile backup = backupService.importFile(file);
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.BACKUP_IMPORTED, backup.name()));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
        }
        return Endpoints.REDIRECT + Endpoints.BACKUPS;
    }

    @PostMapping(Endpoints.BACKUP_DELETE)
    public String delete(@PathVariable String name, RedirectAttributes redirectAttributes) {
        try {
            backupService.delete(name);
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.BACKUP_DELETED, name));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
        }
        return Endpoints.REDIRECT + Endpoints.BACKUPS;
    }

    /**
     * Restores a backup, then signs everyone out: the users, their profiles and passwords are those of
     * the backup now.
     */
    @PostMapping(Endpoints.BACKUP_RESTORE)
    public String restore(@PathVariable String name, @RequestParam(defaultValue = "") String confirmation,
                          HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (!isRestoreConfirmed(confirmation)) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.get(MessageProperties.BACKUP_RESTORE_CANCELLED,
                messages.get(MessageProperties.BACKUP_RESTORE_WORD)));
            return Endpoints.REDIRECT + Endpoints.BACKUPS;
        }
        try {
            backupService.restore(name);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
            return Endpoints.REDIRECT + Endpoints.BACKUPS;
        }
        sessionService.expireAll();
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return Endpoints.REDIRECT + Endpoints.LOGIN + "?restored";
    }

    /** The confirmation word of any language: the user may have switched language since the page was shown. */
    private boolean isRestoreConfirmed(String confirmation) {
        String typed = confirmation.trim().toUpperCase();
        if (RESTORE_CONFIRMATION.equals(typed)) {
            return true;
        }
        for (Language language : Language.values()) {
            if (messages.get(language, MessageProperties.BACKUP_RESTORE_WORD).toUpperCase().equals(typed)) {
                return true;
            }
        }
        return false;
    }

    private String size(BackupFile backup) {
        return backup.isMegabytes() ? messages.get(MessageProperties.BACKUP_SIZE_MB, backup.sizeInMegabytes())
            : messages.get(MessageProperties.BACKUP_SIZE_KB, backup.sizeInKilobytes());
    }
}
