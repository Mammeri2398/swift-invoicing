package com.swiftlabs.invoicing.dashboard;

import com.swiftlabs.invoicing.client.ClientService;
import com.swiftlabs.invoicing.config.InvoiceProperties;
import com.swiftlabs.invoicing.invoice.InvoiceService;
import com.swiftlabs.invoicing.invoice.InvoiceStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final InvoiceService invoiceService;
    private final ClientService clientService;
    private final InvoiceProperties properties;

    public DashboardService(InvoiceService invoiceService, ClientService clientService,
                            InvoiceProperties properties) {
        this.invoiceService = invoiceService;
        this.clientService = clientService;
        this.properties = properties;
    }

    /** Stats for the given year, or the current year when null. */
    public DashboardStats stats(Integer year) {
        int y = year != null ? year : LocalDate.now().getYear();
        return new DashboardStats(y, invoiceService.totalBilled(y), properties.annualCeiling(),
            invoiceService.findRecent(),
            invoiceService.countByStatus(InvoiceStatus.ISSUED), invoiceService.totalByStatus(InvoiceStatus.ISSUED),
            invoiceService.countByStatus(InvoiceStatus.DRAFT), clientService.count());
    }
}
