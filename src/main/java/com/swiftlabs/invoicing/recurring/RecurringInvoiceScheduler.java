package com.swiftlabs.invoicing.recurring;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Prepares the due recurring invoices. An office PC is not on at a fixed hour: the check runs a minute after the
 * start, then every hour (scheduling is enabled by BackupScheduler). Off with app.recurring.scheduler=false (tests).
 */
@Configuration
@ConditionalOnProperty(name = "app.recurring.scheduler", havingValue = "true", matchIfMissing = true)
public class RecurringInvoiceScheduler {

    private final RecurringInvoiceService service;

    public RecurringInvoiceScheduler(RecurringInvoiceService service) {
        this.service = service;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1H")
    public void prepareDueInvoices() {
        service.generateDue();
    }
}
