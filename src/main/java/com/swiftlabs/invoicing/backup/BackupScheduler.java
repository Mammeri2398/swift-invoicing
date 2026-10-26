package com.swiftlabs.invoicing.backup;

import com.swiftlabs.invoicing.common.BusinessException;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * The daily backup. An office PC is not on at a fixed hour, so instead of a time of day the check runs
 * every hour (and a few minutes after start): a backup is made when the latest one is a day old.
 * A failure is retried at the next check and shown in the application meanwhile.
 */
@Configuration
@EnableScheduling
public class BackupScheduler {

    private final BackupService backupService;

    public BackupScheduler(BackupService backupService) {
        this.backupService = backupService;
    }

    @Scheduled(initialDelayString = "PT3M", fixedDelayString = "PT1H")
    public void backupWhenDue() {
        if (backupService.isAvailable() && backupService.isAutomaticBackupDue()) {
            try {
                backupService.backup(BackupKind.AUTO);
            } catch (BusinessException ex) {
                // Logged and kept as the last failure by the service
            }
        }
    }
}
