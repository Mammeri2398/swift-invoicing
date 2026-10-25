package com.swiftlabs.invoicing.dashboard;

import com.swiftlabs.invoicing.client.ClientService;
import com.swiftlabs.invoicing.invoice.InvoiceService;
import com.swiftlabs.invoicing.invoice.InvoiceStatus;
import com.swiftlabs.invoicing.issuer.Issuer;
import com.swiftlabs.invoicing.issuer.IssuerService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final InvoiceService invoiceService;
    private final ClientService clientService;
    private final IssuerService issuerService;

    public DashboardService(InvoiceService invoiceService, ClientService clientService,
                            IssuerService issuerService) {
        this.invoiceService = invoiceService;
        this.clientService = clientService;
        this.issuerService = issuerService;
    }

    /** Stats for the given year, or the current year when null. */
    public DashboardStats stats(Integer year) {
        int y = year != null ? year : LocalDate.now().getYear();
        Issuer issuer = issuerService.getIssuer();
        return new DashboardStats(y, invoiceService.totalBilled(y), issuer.getAnnualCeiling(), !issuer.isCompany(),
            invoiceService.findRecent(),
            invoiceService.countByStatus(InvoiceStatus.ISSUED), invoiceService.totalByStatus(InvoiceStatus.ISSUED),
            invoiceService.countByStatus(InvoiceStatus.DRAFT), clientService.count());
    }
}
