package com.swiftlabs.invoicing.dashboard;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping({"/", "/dashboard"})
    @PreAuthorize("hasAuthority('VIEW_DASHBOARD')")
    public String dashboard(@RequestParam(name = "year", required = false) Integer year, Model model) {
        model.addAttribute("stats", dashboardService.stats(year));
        return "dashboard/index";
    }
}
