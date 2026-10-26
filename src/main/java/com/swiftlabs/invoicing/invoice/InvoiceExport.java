package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Invoice list for the accountant, as CSV (French Excel conventions: ";" separator, decimal comma,
 * UTF-8 with BOM) or as an Excel workbook with real dates and numbers. Headers and labels are in the
 * user's language; number and date formats are the same in every language.
 */
@Component
public class InvoiceExport {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** One exported document, with its payment summary (invoices only). */
    private record ExportRow(Invoice invoice, PaymentService.Summary payment) {
    }

    /** The columns, in order: header (a message key) and how to read the value from a row. */
    private record Column(String headerKey, int width, Function<ExportRow, Object> value) {
    }

    /** A column read from the document alone. */
    private static Column col(String headerKey, int width, Function<Invoice, Object> value) {
        return new Column("invoiceExport.col." + headerKey, width, row -> value.apply(row.invoice()));
    }

    private final Messages messages;
    private final List<Column> columns;

    public InvoiceExport(Messages messages) {
        this.messages = messages;
        // Headers and labels (status, payment method, type) in the current request's language
        this.columns = List.of(
            col("number", 14, i -> i.getInvoiceNumber() != null ? i.getInvoiceNumber() : messages.label(InvoiceStatus.DRAFT)),
            col("status", 11, i -> messages.label(i.getStatus())),
            col("issueDate", 14, Invoice::getIssueDate),
            col("dueDate", 12, Invoice::getDueDate),
            // Issued invoices: the client as frozen on the invoice; drafts: the current client
            col("client", 32, i -> i.getClientName() != null ? i.getClientName() : i.getClient().getName()),
            col("clientNif", 22, i -> i.isDraft() ? i.getClient().getNif() : i.getClientNif()),
            col("paymentMethod", 18, i -> i.getPaymentMethod() != null ? messages.label(i.getPaymentMethod()) : null),
            // Credit notes negative, so that a column sum is the net amount
            col("subtotal", 16, i -> signed(i, i.getSubtotalAmount())),
            col("vat", 14, i -> signed(i, i.getVatAmount())),
            col("total", 16, i -> signed(i, i.getTotalAmount())),
            col("issuedAt", 17, Invoice::getIssuedAt),
            col("paidAt", 17, Invoice::getPaidAt),
            col("cancelledAt", 17, Invoice::getCancelledAt),
            col("kind", 10, i -> messages.label(i.getKind())),
            col("originalInvoice", 16, i -> i.getOriginalInvoice() != null ? i.getOriginalInvoice().getInvoiceNumber() : null),
            col("stampDuty", 16, Invoice::getStampDutyAmount),
            // Invoices only: what was received and what is left (credit notes: blank)
            new Column(MessageProperties.INVOICE_EXPORT_COL_PAID, 14, row -> row.payment() != null ? row.payment().paid() : null),
            new Column(MessageProperties.INVOICE_EXPORT_COL_BALANCE, 14, row -> row.payment() != null ? row.payment().balance() : null));
    }

    private String header(Column column) {
        return messages.get(column.headerKey());
    }

    /** {@code payments}: summary of each invoice by id (see PaymentService#summaries). */
    public byte[] toCsv(List<Invoice> invoices, Map<Long, PaymentService.Summary> payments) {
        StringBuilder csv = new StringBuilder("﻿");
        appendCsvRow(csv, columns.stream().map(this::header).map(Object.class::cast).toList());
        for (Invoice invoice : invoices) {
            ExportRow row = new ExportRow(invoice, payments.get(invoice.getId()));
            appendCsvRow(csv, columns.stream().map(c -> c.value().apply(row)).toList());
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] toXlsx(List<Invoice> invoices, Map<Long, PaymentService.Summary> payments) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(WorkbookUtil.createSafeSheetName(messages.get(MessageProperties.INVOICE_EXPORT_SHEET)));
            CellStyle header = headerStyle(workbook);
            CellStyle date = formatStyle(workbook, "dd/mm/yyyy");
            CellStyle dateTime = formatStyle(workbook, "dd/mm/yyyy hh:mm");
            CellStyle amount = formatStyle(workbook, "#,##0.00");

            Row headerRow = sheet.createRow(0);
            for (int c = 0; c < columns.size(); c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(header(columns.get(c)));
                cell.setCellStyle(header);
                sheet.setColumnWidth(c, columns.get(c).width() * 256);
            }
            int r = 1;
            for (Invoice invoice : invoices) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < columns.size(); c++) {
                    Object value = columns.get(c).value().apply(new ExportRow(invoice, payments.get(invoice.getId())));
                    Cell cell = row.createCell(c);
                    // Text is always written as a string cell: never interpreted as a formula
                    if (value == null) {
                        cell.setBlank();
                    } else if (value instanceof LocalDate d) {
                        cell.setCellValue(d);
                        cell.setCellStyle(date);
                    } else if (value instanceof LocalDateTime d) {
                        cell.setCellValue(d);
                        cell.setCellStyle(dateTime);
                    } else if (value instanceof BigDecimal n) {
                        cell.setCellValue(n.doubleValue());
                        cell.setCellStyle(amount);
                    } else {
                        cell.setCellValue(value.toString());
                    }
                }
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(r - 1, 0), 0, columns.size() - 1));
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void appendCsvRow(StringBuilder csv, List<Object> values) {
        for (int c = 0; c < values.size(); c++) {
            if (c > 0) {
                csv.append(';');
            }
            csv.append(csvField(values.get(c)));
        }
        csv.append("\r\n");
    }

    private static String csvField(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDate d) {
            return d.format(DATE);
        }
        if (value instanceof LocalDateTime d) {
            return d.format(DATE_TIME);
        }
        if (value instanceof BigDecimal n) {
            return n.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
        }
        return csvText(value.toString());
    }

    /**
     * Quotes text when needed, and neutralises a leading =, +, - or @ so that a client name can
     * never run as a formula when the file is opened in a spreadsheet (CSV injection).
     */
    private static String csvText(String text) {
        String safe = !text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0 ? "'" + text : text;
        if (safe.contains(";") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private static CellStyle headerStyle(XSSFWorkbook workbook) {
        Font bold = workbook.createFont();
        bold.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(bold);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private static CellStyle formatStyle(XSSFWorkbook workbook, String format) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat(format));
        return style;
    }

    private static BigDecimal signed(Invoice invoice, BigDecimal amount) {
        return invoice.isCreditNote() ? amount.negate() : amount;
    }
}
