package com.swiftlabs.invoicing.backup;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.company.Company;
import com.swiftlabs.invoicing.company.CompanyContext;
import com.swiftlabs.invoicing.company.CompanyService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * The daily backup of each company, and of the data they share. An office PC is not on at a fixed hour, so instead
 * of a time of day the check runs every hour (and a few minutes after start): a backup is made when the latest one
 * is a day old.
 * A failure is retried at the next check and shown in the application meanwhile.
 */
@Configuration
@EnableScheduling
public class BackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);

    private final BackupService backupService;
    private final CompanyService companyService;

    public BackupScheduler(BackupService backupService, CompanyService companyService) {
        this.backupService = backupService;
        this.companyService = companyService;
    }

    /** Each active company in turn (its own folder settings and retention), then the data they share. */
    @Scheduled(initialDelayString = "PT3M", fixedDelayString = "PT1H")
    public void backupWhenDue() {
        if (!backupService.isAvailable()) {
            return;
        }
        for (Company company : companyService.findActive()) {
            // One company's failure (its schema unreachable, an unexpected error) never stops the others
            try {
                CompanyContext.runAs(company.getSchemaName(), () -> {
                    if (backupService.isAutomaticBackupDue()) {
                        backupService.backup(BackupKind.AUTO);
                    }
                });
            } catch (BusinessException ex) {
                // Logged and kept as the company's last failure by the service
            } catch (RuntimeException ex) {
                log.error("Automatic backup of {} failed", company.getSchemaName(), ex);
            }
        }
        try {
            if (backupService.isSharedBackupDue()) {
                backupService.backupShared();
            }
        } catch (BusinessException ex) {
            // Logged by the service
        } catch (RuntimeException ex) {
            log.error("Automatic backup of the shared data failed", ex);
        }
    }
}
