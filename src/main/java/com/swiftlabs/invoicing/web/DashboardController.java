package com.swiftlabs.invoicing.web;

import com.swiftlabs.invoicing.service.InvoiceService;
import com.swiftlabs.invoicing.service.IssuerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DashboardController {

    private final InvoiceService invoiceService;
    private final IssuerService issuerService;

    public DashboardController(InvoiceService invoiceService, IssuerService issuerService) {
        this.invoiceService = invoiceService;
        this.issuerService = issuerService;
    }

    @GetMapping("/")
    public String dashboard(@RequestParam(name = "year", required = false) Integer year, Model model) {
        InvoiceService.DashboardStats stats = invoiceService.dashboard(year);
        model.addAttribute("stats", stats);
        model.addAttribute("ceilingFormatted", com.swiftlabs.invoicing.util.AmountUtils.format(stats.getCeiling()));
        model.addAttribute("totalFormatted", com.swiftlabs.invoicing.util.AmountUtils.format(stats.getTotalAmount()));
        model.addAttribute("vatMention", issuerService.getVatMention());
        return "dashboard";
    }
}
