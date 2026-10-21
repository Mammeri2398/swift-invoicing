package com.swiftlabs.invoicing.web;

import com.swiftlabs.invoicing.service.InvoiceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LookupController {

    private final InvoiceService invoiceService;

    public LookupController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping("/invoices/lookup")
    public String lookup(@RequestParam(name = "number", required = false) String number, Model model) {
        if (number != null && !number.isBlank()) {
            com.swiftlabs.invoicing.domain.Invoice invoice = invoiceService.findByNumber(number.trim());
            model.addAttribute("number", number.trim());
            model.addAttribute("found", invoice != null);
            model.addAttribute("invoice", invoice);
        }
        return "lookup";
    }
}
