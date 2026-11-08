package com.swiftlabs.invoicing.recurring;

import com.swiftlabs.invoicing.company.Company;
import com.swiftlabs.invoicing.company.CompanyContext;
import com.swiftlabs.invoicing.company.CompanyService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(RecurringInvoiceScheduler.class);

    private final RecurringInvoiceService service;
    private final CompanyService companyService;

    public RecurringInvoiceScheduler(RecurringInvoiceService service, CompanyService companyService) {
        this.service = service;
        this.companyService = companyService;
    }

    /** Each active company in turn: its drafts are prepared in its own schema. */
    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1H")
    public void prepareDueInvoices() {
        for (Company company : companyService.findActive()) {
            // One company's failure (its schema unreachable, an unexpected error) never stops the others
            try {
                CompanyContext.runAs(company.getSchemaName(), service::generateDue);
            } catch (RuntimeException ex) {
                log.error("Recurring invoices of {} not prepared", company.getSchemaName(), ex);
            }
        }
    }
}
