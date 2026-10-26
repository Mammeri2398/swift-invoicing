package com.swiftlabs.invoicing.dashboard;

import com.swiftlabs.invoicing.common.Language;
import com.swiftlabs.invoicing.invoice.DocumentKind;
import com.swiftlabs.invoicing.invoice.InvoiceService;
import com.swiftlabs.invoicing.invoice.InvoiceStatus;
import com.swiftlabs.invoicing.invoice.MonthlyTotal;
import com.swiftlabs.invoicing.issuer.Issuer;
import com.swiftlabs.invoicing.issuer.IssuerService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class TurnoverService {

    private final InvoiceService invoiceService;
    private final IssuerService issuerService;

    public TurnoverService(InvoiceService invoiceService, IssuerService issuerService) {
        this.invoiceService = invoiceService;
        this.issuerService = issuerService;
    }

    /** The report of a year, month names in French (outside a web request: no user language). */
    public TurnoverReport report(int year) {
        return report(year, Language.FR);
    }

    /** The report of a year, with the month names in the given language. */
    public TurnoverReport report(int year, Language language) {
        List<MonthlyTotal> totals = invoiceService.monthlyTotals(year);
        Issuer issuer = issuerService.getIssuer();
        BigDecimal ceiling = issuer.getAnnualCeiling();
        List<TurnoverReport.Month> months = new ArrayList<>();
        BigDecimal cumulative = BigDecimal.ZERO;
        for (int m = 1; m <= 12; m++) {
            BigDecimal paid = BigDecimal.ZERO;
            BigDecimal pending = BigDecimal.ZERO;
            BigDecimal cancelled = BigDecimal.ZERO;
            BigDecimal vat = BigDecimal.ZERO;
            BigDecimal credited = BigDecimal.ZERO;
            long count = 0;
            long cancelledCount = 0;
            for (MonthlyTotal total : totals) {
                if (total.month() != m) {
                    continue;
                }
                if (total.kind() == DocumentKind.CREDIT_NOTE) {
                    credited = credited.add(total.amount());
                    vat = vat.subtract(total.vat());
                } else if (total.status() == InvoiceStatus.CANCELLED) {
                    cancelledCount += total.count();
                    cancelled = cancelled.add(total.amount());
                } else {
                    count += total.count();
                    vat = vat.add(total.vat());
                    if (total.status() == InvoiceStatus.PAID) {
                        paid = paid.add(total.amount());
                    } else {
                        pending = pending.add(total.amount());
                    }
                }
            }
            BigDecimal billed = paid.add(pending).subtract(credited);
            cumulative = cumulative.add(billed);
            boolean overCeiling = !issuer.isCompany() && cumulative.compareTo(ceiling) >= 0 && billed.signum() > 0;
            months.add(new TurnoverReport.Month(m, monthName(m, TextStyle.FULL_STANDALONE, language),
                monthName(m, TextStyle.SHORT_STANDALONE, language), count, billed, paid, pending, credited, vat,
                cancelledCount, cancelled, cumulative, overCeiling));
        }
        return new TurnoverReport(year, months, ceiling, issuer.isCompany());
    }

    /** Years offered in the selector: the years with invoices, and always the current one. */
    public List<Integer> years(int currentYear) {
        List<Integer> years = new ArrayList<>(invoiceService.invoiceYears());
        if (!years.contains(currentYear)) {
            years.add(0, currentYear);
        }
        return years;
    }

    /** "Janvier" / "Janv.", "January" / "Jan", "جانفي" (ar-DZ: the month names used in Algeria). */
    private static String monthName(int month, TextStyle style, Language language) {
        Locale locale = language.getLocale();
        String name = Month.of(month).getDisplayName(style, locale);
        return name.substring(0, 1).toUpperCase(locale) + name.substring(1);
    }
}
