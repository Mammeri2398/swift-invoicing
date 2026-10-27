package com.swiftlabs.invoicing.accounting;

import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.common.Privileges;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Comptabilité: the entries of a period for the accountant, shown and exported (Excel, CSV). By default the previous
 * month. The accounts the entries use are changed by whoever edits the company settings.
 */
@Controller
@RequestMapping(Endpoints.ACCOUNTING)
@PreAuthorize(Privileges.Has.VIEW_DASHBOARD)
public class AccountingController {

    /** Entries shown on the page; the files always hold them all. */
    static final int PREVIEW_LIMIT = 300;

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    /** A ready-made period of the page: its label (a message key) and its dates. */
    public record Period(String labelKey, LocalDate from, LocalDate to) {
    }

    private final AccountingService accountingService;
    private final AccountingExport export;
    private final Messages messages;
    private final Clock clock;

    public AccountingController(AccountingService accountingService, AccountingExport export, Messages messages, Clock clock) {
        this.accountingService = accountingService;
        this.export = export;
        this.messages = messages;
        this.clock = clock;
    }

    @GetMapping
    public String page(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, Model model,
                       Authentication authentication) {
        if (!model.containsAttribute(CommonCodes.Attributes.ACCOUNTS)) {
            model.addAttribute(CommonCodes.Attributes.ACCOUNTS, AccountingForm.from(accountingService.settings()));
        }
        return render(period(from, to), model, authentication);
    }

    @GetMapping(Endpoints.ACCOUNTING_XLSX)
    public ResponseEntity<byte[]> xlsx(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Period period = period(from, to);
        return file(fileName(period, "xlsx"), XLSX, export.toXlsx(accountingService.journal(period.from(), period.to())));
    }

    @GetMapping(Endpoints.ACCOUNTING_CSV)
    public ResponseEntity<byte[]> csv(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Period period = period(from, to);
        return file(fileName(period, "csv"), new MediaType("text", "csv", StandardCharsets.UTF_8),
            export.toCsv(accountingService.journal(period.from(), period.to())));
    }

    @PostMapping(Endpoints.ACCOUNTING_ACCOUNTS)
    @PreAuthorize(Privileges.Has.VIEW_DASHBOARD + " and " + Privileges.Has.EDIT_ISSUER)
    public String saveAccounts(@Valid @ModelAttribute(CommonCodes.Attributes.ACCOUNTS) AccountingForm form, BindingResult result,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               Model model, Authentication authentication, RedirectAttributes redirectAttributes) {
        Period period = period(from, to);
        if (result.hasErrors()) {
            return render(period, model, authentication);
        }
        accountingService.save(form);
        redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.ACCOUNTING_ACCOUNTS_SAVED));
        redirectAttributes.addAttribute("from", period.from());
        redirectAttributes.addAttribute("to", period.to());
        return Endpoints.REDIRECT + Endpoints.ACCOUNTING;
    }

    private String render(Period period, Model model, Authentication authentication) {
        // The accounts are shown to everyone, changed only with the right to edit the company settings
        model.addAttribute(CommonCodes.Attributes.CAN_EDIT, authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals(Privileges.EDIT_ISSUER)));
        model.addAttribute(CommonCodes.Attributes.JOURNAL, accountingService.journal(period.from(), period.to()));
        model.addAttribute(CommonCodes.Attributes.PERIODS, presets());
        model.addAttribute(CommonCodes.Attributes.PREVIEW_LIMIT, PREVIEW_LIMIT);
        return CommonCodes.Views.ACCOUNTING_INDEX;
    }

    /** The dates asked (in order), else the previous month. */
    private Period period(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return presets().get(0);
        }
        return from.isAfter(to) ? new Period(null, to, from) : new Period(null, from, to);
    }

    /** Previous month first: the default, the one usually sent to the accountant. */
    private List<Period> presets() {
        LocalDate today = LocalDate.now(clock);
        LocalDate month = today.withDayOfMonth(1);
        LocalDate previousMonth = month.minusMonths(1);
        LocalDate quarter = month.withMonth((today.getMonthValue() - 1) / 3 * 3 + 1);
        LocalDate year = today.withDayOfYear(1);
        return List.of(
            new Period(MessageProperties.ACCOUNTING_PERIOD_PREVIOUS_MONTH, previousMonth, month.minusDays(1)),
            new Period(MessageProperties.ACCOUNTING_PERIOD_CURRENT_MONTH, month, month.plusMonths(1).minusDays(1)),
            new Period(MessageProperties.ACCOUNTING_PERIOD_PREVIOUS_QUARTER, quarter.minusMonths(3), quarter.minusDays(1)),
            new Period(MessageProperties.ACCOUNTING_PERIOD_CURRENT_YEAR, year, year.plusYears(1).minusDays(1)),
            new Period(MessageProperties.ACCOUNTING_PERIOD_PREVIOUS_YEAR, year.minusYears(1), year.minusDays(1)));
    }

    /** "comptabilite-2026-09-01-2026-09-30.xlsx" */
    private String fileName(Period period, String extension) {
        return messages.get(MessageProperties.ACCOUNTING_EXPORT_FILE) + "-" + period.from() + "-" + period.to() + "." + extension;
    }

    private static ResponseEntity<byte[]> file(String name, MediaType type, byte[] body) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
            .contentType(type)
            .body(body);
    }
}
