package com.swiftlabs.invoicing.backup;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Exposes {@code backupWarning} to the views of the users who manage backups: the last backup failed,
 * or none was made for days (the PC was off, the folder is a USB drive that was removed...).
 */
@ControllerAdvice
public class BackupAdvice {

    /** No backup for this long: the warning shows, even without a failure. */
    static final int STALE_DAYS = 3;

    private final BackupService backupService;
    private final Clock clock;

    public BackupAdvice(BackupService backupService, Clock clock) {
        this.backupService = backupService;
        this.clock = clock;
    }

    @ModelAttribute("backupWarning")
    public String backupWarning(Authentication authentication) {
        if (authentication == null || !backupService.isAvailable()
            || authentication.getAuthorities().stream().noneMatch(authority -> authority.getAuthority().equals("MANAGE_BACKUPS"))) {
            return null;
        }
        try {
            if (backupService.lastFailure() != null) {
                return "Problème de sauvegarde : " + backupService.lastFailure().message();
            }
            LocalDateTime staleBefore = LocalDateTime.now(clock).minusDays(STALE_DAYS);
            Optional<BackupFile> latest = backupService.latest();
            if (latest.isEmpty()) {
                return "Aucune sauvegarde n'a encore été faite.";
            }
            return latest.get().createdAt().isAfter(staleBefore) ? null : "Aucune sauvegarde depuis plus de " + STALE_DAYS + " jours.";
        } catch (RuntimeException ex) {
            // Never break a page over the warning
            return null;
        }
    }
}
