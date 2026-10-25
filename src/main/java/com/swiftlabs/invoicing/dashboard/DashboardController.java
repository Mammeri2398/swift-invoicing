package com.swiftlabs.invoicing.dashboard;

import com.swiftlabs.invoicing.invoice.PaymentService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;
    private final PaymentService paymentService;

    public DashboardController(DashboardService dashboardService, PaymentService paymentService) {
        this.dashboardService = dashboardService;
        this.paymentService = paymentService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('VIEW_DASHBOARD')")
    public String dashboard(@RequestParam(name = "year", required = false) Integer year, Model model) {
        model.addAttribute("stats", dashboardService.stats(year));
        List<PaymentService.Overdue> overdue = paymentService.overdue();
        model.addAttribute("overdue", overdue);
        model.addAttribute("overdueTotal", overdue.stream().map(PaymentService.Overdue::balance).reduce(BigDecimal.ZERO, BigDecimal::add));
        return "dashboard/index";
    }
}
