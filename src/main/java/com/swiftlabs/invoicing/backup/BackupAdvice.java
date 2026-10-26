package com.swiftlabs.invoicing.backup;

import com.swiftlabs.invoicing.common.Messages;

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
    private final Messages messages;

    public BackupAdvice(BackupService backupService, Clock clock, Messages messages) {
        this.backupService = backupService;
        this.clock = clock;
        this.messages = messages;
    }

    @ModelAttribute("backupWarning")
    public String backupWarning(Authentication authentication) {
        if (authentication == null || !backupService.isAvailable()
            || authentication.getAuthorities().stream().noneMatch(authority -> authority.getAuthority().equals("MANAGE_BACKUPS"))) {
            return null;
        }
        try {
            BackupService.Failure failure = backupService.lastFailure();
            if (failure != null) {
                return messages.get("backup.warning", messages.of(failure.error()));
            }
            LocalDateTime staleBefore = LocalDateTime.now(clock).minusDays(STALE_DAYS);
            Optional<BackupFile> latest = backupService.latest();
            if (latest.isEmpty()) {
                return messages.get("backup.warning.none");
            }
            return latest.get().createdAt().isAfter(staleBefore) ? null : messages.get("backup.warning.stale", String.valueOf(STALE_DAYS));
        } catch (RuntimeException ex) {
            // Never break a page over the warning
            return null;
        }
    }
}
