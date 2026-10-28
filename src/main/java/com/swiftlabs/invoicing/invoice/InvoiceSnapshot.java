package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.common.AmountUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What a draft holds, as plain values: taken before a save and compared with the saved draft ({@link #changesTo}),
 * to list in the history what the edit changed. Values are written without formatting (plain numbers, ISO dates,
 * codes), so that the history shows them in the reader's language.
 */
record InvoiceSnapshot(String client, String issueDate, String dueDate, String paymentMethod, String notes, List<Line> lines,
                       String subtotal, String vat, String stampDuty, String amountDue) {

    record Line(String description, String unit, String quantity, String unitPrice, String vatRate, String total) {
    }

    /** One changed value; {@code line} is the line number for the fields of a line. */
    record Change(InvoiceChange.Field field, Integer line, String oldValue, String newValue) {
    }

    static InvoiceSnapshot of(Invoice invoice) {
        List<Line> lines = new ArrayList<>();
        for (InvoiceLine line : invoice.getLines()) {
            lines.add(new Line(line.getDescription(), blankToNull(line.getUnit()), plain(line.getQuantity()),
                plain(AmountUtils.price(line.getUnitPrice())), plain(line.getVatRate()), amount(line.getLineTotal())));
        }
        return new InvoiceSnapshot(clientName(invoice), String.valueOf(invoice.getIssueDate()),
            invoice.getDueDate() != null ? invoice.getDueDate().toString() : null,
            invoice.getPaymentMethod() != null ? invoice.getPaymentMethod().name() : null, blankToNull(invoice.getNotes()), lines,
            amount(invoice.getSubtotalAmount()), amount(invoice.getVatAmount()), amount(invoice.getStampDutyAmount()),
            amount(invoice.getAmountDue()));
    }

    /**
     * Every value that differs in {@code after}, in the order of the form: the details, each line (compared by
     * position: added at the end, removed from the end, or changed), then the totals.
     */
    List<Change> changesTo(InvoiceSnapshot after) {
        List<Change> changes = new ArrayList<>();
        add(changes, InvoiceChange.Field.CLIENT, null, client, after.client);
        add(changes, InvoiceChange.Field.ISSUE_DATE, null, issueDate, after.issueDate);
        add(changes, InvoiceChange.Field.DUE_DATE, null, dueDate, after.dueDate);
        add(changes, InvoiceChange.Field.PAYMENT_METHOD, null, paymentMethod, after.paymentMethod);
        add(changes, InvoiceChange.Field.NOTES, null, notes, after.notes);
        for (int i = 0; i < Math.max(lines.size(), after.lines.size()); i++) {
            int number = i + 1;
            Line before = i < lines.size() ? lines.get(i) : null;
            Line now = i < after.lines.size() ? after.lines.get(i) : null;
            if (before == null) {
                changes.add(new Change(InvoiceChange.Field.LINE_ADDED, number, null, now.description()));
                add(changes, InvoiceChange.Field.LINE_UNIT, number, null, now.unit());
                add(changes, InvoiceChange.Field.LINE_QUANTITY, number, null, now.quantity());
                add(changes, InvoiceChange.Field.LINE_UNIT_PRICE, number, null, now.unitPrice());
                addRate(changes, number, null, now.vatRate());
                add(changes, InvoiceChange.Field.LINE_TOTAL, number, null, now.total());
            } else if (now == null) {
                changes.add(new Change(InvoiceChange.Field.LINE_REMOVED, number, before.description(), null));
                add(changes, InvoiceChange.Field.LINE_TOTAL, number, before.total(), null);
            } else {
                add(changes, InvoiceChange.Field.LINE_DESCRIPTION, number, before.description(), now.description());
                add(changes, InvoiceChange.Field.LINE_UNIT, number, before.unit(), now.unit());
                add(changes, InvoiceChange.Field.LINE_QUANTITY, number, before.quantity(), now.quantity());
                add(changes, InvoiceChange.Field.LINE_UNIT_PRICE, number, before.unitPrice(), now.unitPrice());
                add(changes, InvoiceChange.Field.LINE_VAT_RATE, number, before.vatRate(), now.vatRate());
                add(changes, InvoiceChange.Field.LINE_TOTAL, number, before.total(), now.total());
            }
        }
        add(changes, InvoiceChange.Field.SUBTOTAL, null, subtotal, after.subtotal);
        add(changes, InvoiceChange.Field.VAT, null, vat, after.vat);
        add(changes, InvoiceChange.Field.STAMP_DUTY, null, stampDuty, after.stampDuty);
        add(changes, InvoiceChange.Field.AMOUNT_DUE, null, amountDue, after.amountDue);
        return changes;
    }

    private static void add(List<Change> changes, InvoiceChange.Field field, Integer line, String before, String after) {
        if (!Objects.equals(before, after)) {
            changes.add(new Change(field, line, before, after));
        }
    }

    /** An added line's TVA rate: not when none (auto-entrepreneur, exempt line). */
    private static void addRate(List<Change> changes, int line, String before, String after) {
        if (after != null && new BigDecimal(after).signum() != 0) {
            add(changes, InvoiceChange.Field.LINE_VAT_RATE, line, before, after);
        }
    }

    private static String clientName(Invoice invoice) {
        if (invoice.getClient() != null) {
            return invoice.getClient().getName();
        }
        return invoice.getClientName();
    }

    private static String plain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
    }

    private static String amount(BigDecimal value) {
        return value == null ? null : AmountUtils.round2(value).toPlainString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
