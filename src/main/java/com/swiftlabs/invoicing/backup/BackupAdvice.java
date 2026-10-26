package com.swiftlabs.invoicing.backup;

import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.common.Privileges;

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

    @ModelAttribute(CommonCodes.Attributes.BACKUP_WARNING)
    public String backupWarning(Authentication authentication) {
        if (authentication == null || !backupService.isAvailable()
            || authentication.getAuthorities().stream().noneMatch(authority -> authority.getAuthority().equals(Privileges.MANAGE_BACKUPS))) {
            return null;
        }
        try {
            BackupService.Failure failure = backupService.lastFailure();
            if (failure != null) {
                return messages.get(MessageProperties.BACKUP_WARNING, messages.of(failure.error()));
            }
            LocalDateTime staleBefore = LocalDateTime.now(clock).minusDays(STALE_DAYS);
            Optional<BackupFile> latest = backupService.latest();
            if (latest.isEmpty()) {
                return messages.get(MessageProperties.BACKUP_WARNING_NONE);
            }
            return latest.get().createdAt().isAfter(staleBefore) ? null : messages.get(MessageProperties.BACKUP_WARNING_STALE,
                String.valueOf(STALE_DAYS));
        } catch (RuntimeException ex) {
            // Never break a page over the warning
            return null;
        }
    }
}
