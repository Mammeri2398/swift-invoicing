package com.swiftlabs.invoicing.vat;

import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.common.Privileges;
import com.swiftlabs.invoicing.dashboard.TurnoverService;
import com.swiftlabs.invoicing.issuer.IssuerService;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TVA du mois: the help for the monthly G50 declaration of a company. By default the previous month, the one being
 * declared (the G50 is filed in the first days of the next month).
 */
@Controller
@RequestMapping(Endpoints.VAT)
@PreAuthorize(Privileges.Has.VIEW_DASHBOARD)
public class VatController {

    private final VatSummaryService vatService;
    private final VatExport vatExport;
    private final TurnoverService turnoverService;
    private final IssuerService issuerService;
    private final Messages messages;
    private final Clock clock;

    public VatController(VatSummaryService vatService, VatExport vatExport, TurnoverService turnoverService,
                         IssuerService issuerService, Messages messages, Clock clock) {
        this.vatService = vatService;
        this.vatExport = vatExport;
        this.turnoverService = turnoverService;
        this.issuerService = issuerService;
        this.messages = messages;
        this.clock = clock;
    }

    @GetMapping
    public String page(@RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month,
                       @RequestParam(defaultValue = "INVOICED") VatBasis basis, Model model) {
        LocalDate period = period(year, month);
        model.addAttribute(CommonCodes.Attributes.COMPANY, issuerService.getIssuer().isCompany());
        model.addAttribute(CommonCodes.Attributes.SUMMARY, vatService.month(period.getYear(), period.getMonthValue(), basis));
        model.addAttribute(CommonCodes.Attributes.MONTHS, vatService.year(period.getYear(), basis));
        model.addAttribute(CommonCodes.Attributes.YEARS, turnoverService.years(LocalDate.now(clock).getYear()));
        model.addAttribute(CommonCodes.Attributes.MONTH_NAMES, monthNames());
        model.addAttribute(CommonCodes.Attributes.BASES, VatBasis.values());
        return CommonCodes.Views.VAT_INDEX;
    }

    @GetMapping(Endpoints.VAT_EXPORT)
    public ResponseEntity<byte[]> export(@RequestParam int year, @RequestParam int month,
                                         @RequestParam(defaultValue = "INVOICED") VatBasis basis) {
        LocalDate period = period(year, month);
        VatSummary summary = vatService.month(period.getYear(), period.getMonthValue(), basis);
        String label = monthNames().get(period.getMonthValue() - 1) + " " + period.getYear();
        String file = messages.get(MessageProperties.VAT_EXPORT_FILE) + "-" + period.getYear() + "-"
            + String.format("%02d", period.getMonthValue()) + ".xlsx";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file).build().toString())
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(vatExport.toXlsx(summary, label));
    }

    /** The month asked, else the previous month; an impossible month falls back to it too. */
    private LocalDate period(Integer year, Integer month) {
        LocalDate previous = LocalDate.now(clock).withDayOfMonth(1).minusMonths(1);
        if (year == null || month == null || month < 1 || month > 12 || year < 2000 || year > 2100) {
            return previous;
        }
        return LocalDate.of(year, month, 1);
    }

    /** "Janvier"…, "January"…, "جانفي"… in the user's language. */
    private List<String> monthNames() {
        Locale locale = messages.current().getLocale();
        List<String> names = new ArrayList<>();
        for (Month month : Month.values()) {
            String name = month.getDisplayName(TextStyle.FULL_STANDALONE, locale);
            names.add(name.substring(0, 1).toUpperCase(locale) + name.substring(1));
        }
        return names;
    }
}
