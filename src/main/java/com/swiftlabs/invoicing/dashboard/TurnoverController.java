package com.swiftlabs.invoicing.dashboard;

import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.common.Privileges;

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
    private final Messages messages;

    public TurnoverController(TurnoverService turnoverService, Clock clock, Messages messages) {
        this.turnoverService = turnoverService;
        this.clock = clock;
        this.messages = messages;
    }

    @GetMapping(Endpoints.TURNOVER)
    @PreAuthorize(Privileges.Has.VIEW_DASHBOARD)
    public String turnover(@RequestParam(name = "year", required = false) Integer year, Model model) {
        int currentYear = LocalDate.now(clock).getYear();
        int y = year != null ? year : currentYear;
        model.addAttribute(CommonCodes.Attributes.REPORT, turnoverService.report(y, messages.current()));
        model.addAttribute(CommonCodes.Attributes.YEARS, turnoverService.years(currentYear));
        return CommonCodes.Views.DASHBOARD_TURNOVER;
    }
}
