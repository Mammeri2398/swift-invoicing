package com.swiftlabs.invoicing.audit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * The activity journal keeps {@code app.audit.keep-days} days (a year by default): older entries are deleted a few
 * minutes after start, then every few hours (an office PC is not on at a fixed hour). 0 keeps everything.
 */
@Component
public class AuditRetention {

    private final AuditService auditService;
    private final int keepDays;
    private final Clock clock;

    public AuditRetention(AuditService auditService, @Value("${app.audit.keep-days:365}") int keepDays, Clock clock) {
        this.auditService = auditService;
        this.keepDays = keepDays;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT6H")
    public void deleteOldEntries() {
        if (keepDays > 0) {
            auditService.deleteOlderThan(LocalDate.now(clock).minusDays(keepDays).atStartOfDay());
        }
    }
}
