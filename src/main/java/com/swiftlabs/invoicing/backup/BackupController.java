package com.swiftlabs.invoicing.backup;

import com.swiftlabs.invoicing.access.auth.SessionService;
import com.swiftlabs.invoicing.common.BusinessException;

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
@RequestMapping(BackupController.URL)
@PreAuthorize("hasAuthority('MANAGE_BACKUPS')")
public class BackupController {

    public static final String URL = "/settings/backups";

    /** Typed by the user to confirm a restore: it replaces all the data. */
    static final String RESTORE_CONFIRMATION = "RESTAURER";

    private final BackupService backupService;
    private final SessionService sessionService;

    public BackupController(BackupService backupService, SessionService sessionService) {
        this.backupService = backupService;
        this.sessionService = sessionService;
    }

    @GetMapping
    public String page(Model model) {
        BackupSettings settings = backupService.settings();
        model.addAttribute("enabled", backupService.isEnabled());
        model.addAttribute("available", backupService.isAvailable());
        model.addAttribute("backups", backupService.list());
        model.addAttribute("directory", backupService.directory().toString());
        model.addAttribute("settings", settings);
        model.addAttribute("failure", backupService.lastFailure());
        model.addAttribute("confirmation", RESTORE_CONFIRMATION);
        return "backup/backups";
    }

    @PostMapping("/now")
    public String backupNow(RedirectAttributes redirectAttributes) {
        try {
            BackupFile backup = backupService.backup(BackupKind.MANUAL);
            redirectAttributes.addFlashAttribute("success", "Sauvegarde faite : " + backup.name() + " (" + backup.displaySize() + ").");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:" + URL;
    }

    @PostMapping("/settings")
    public String saveSettings(@RequestParam String directory, @RequestParam(defaultValue = "0") int keepCount,
                               RedirectAttributes redirectAttributes) {
        try {
            backupService.saveSettings(directory, keepCount);
            redirectAttributes.addFlashAttribute("success", "Réglages des sauvegardes enregistrés.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:" + URL;
    }

    @GetMapping("/{name}")
    public ResponseEntity<Resource> download(@PathVariable String name) {
        Path file = backupService.file(name);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(new FileSystemResource(file));
    }

    @PostMapping("/import")
    public String importFile(@RequestParam(name = "file", required = false) MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            BackupFile backup = backupService.importFile(file);
            redirectAttributes.addFlashAttribute("success", "Sauvegarde importée : " + backup.name()
                + ". Elle peut maintenant être restaurée depuis la liste.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:" + URL;
    }

    @PostMapping("/{name}/delete")
    public String delete(@PathVariable String name, RedirectAttributes redirectAttributes) {
        try {
            backupService.delete(name);
            redirectAttributes.addFlashAttribute("success", "Sauvegarde supprimée : " + name + ".");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:" + URL;
    }

    /**
     * Restores a backup, then signs everyone out: the users, their profiles and passwords are those of
     * the backup now.
     */
    @PostMapping("/{name}/restore")
    public String restore(@PathVariable String name, @RequestParam(defaultValue = "") String confirmation,
                          HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (!RESTORE_CONFIRMATION.equals(confirmation.trim().toUpperCase())) {
            redirectAttributes.addFlashAttribute("error", "Restauration annulée : tapez " + RESTORE_CONFIRMATION + " pour confirmer.");
            return "redirect:" + URL;
        }
        try {
            backupService.restore(name);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:" + URL;
        }
        sessionService.expireAll();
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/login?restored";
    }
}
