package com.swiftlabs.invoicing.dashboard;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.LocalDate;

@Controller
public class TurnoverController {

    private final TurnoverService turnoverService;
    private final Clock clock;

    public TurnoverController(TurnoverService turnoverService, Clock clock) {
        this.turnoverService = turnoverService;
        this.clock = clock;
    }

    @GetMapping("/turnover")
    @PreAuthorize("hasAuthority('VIEW_DASHBOARD')")
    public String turnover(@RequestParam(name = "year", required = false) Integer year, Model model) {
        int currentYear = LocalDate.now(clock).getYear();
        int y = year != null ? year : currentYear;
        model.addAttribute("report", turnoverService.report(y));
        model.addAttribute("years", turnoverService.years(currentYear));
        return "dashboard/turnover";
    }
}
