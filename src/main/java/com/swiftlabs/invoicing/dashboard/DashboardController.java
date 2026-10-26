package com.swiftlabs.invoicing.dashboard;

import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.Privileges;
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

    @GetMapping(Endpoints.DASHBOARD)
    @PreAuthorize(Privileges.Has.VIEW_DASHBOARD)
    public String dashboard(@RequestParam(name = "year", required = false) Integer year, Model model) {
        model.addAttribute(CommonCodes.Attributes.STATS, dashboardService.stats(year));
        List<PaymentService.Overdue> overdue = paymentService.overdue();
        model.addAttribute(CommonCodes.Attributes.OVERDUE, overdue);
        model.addAttribute(CommonCodes.Attributes.OVERDUE_TOTAL,
            overdue.stream().map(PaymentService.Overdue::balance).reduce(BigDecimal.ZERO, BigDecimal::add));
        return CommonCodes.Views.DASHBOARD_INDEX;
    }
}
