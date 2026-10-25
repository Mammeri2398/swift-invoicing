package com.swiftlabs.invoicing.invoice;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.LocalDate;

/** Client statement (relevé de compte), by default from January 1st to today. */
@Controller
public class StatementController {

    private final StatementService statementService;
    private final Clock clock;

    public StatementController(StatementService statementService, Clock clock) {
        this.statementService = statementService;
        this.clock = clock;
    }

    @GetMapping("/clients/{id}/statement")
    @PreAuthorize("hasAuthority('VIEW_INVOICES')")
    public String statement(@PathVariable Long id,
                            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            Model model) {
        LocalDate today = LocalDate.now(clock);
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.withDayOfYear(1);
        if (start.isAfter(end)) {
            start = end;
        }
        model.addAttribute("statement", statementService.statement(id, start, end));
        return "invoice/statement";
    }
}
