package com.swiftlabs.invoicing.invoice;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
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
import java.util.function.Function;

/**
 * Invoice list for the accountant, as CSV (French Excel conventions: ";" separator, decimal comma,
 * UTF-8 with BOM) or as an Excel workbook with real dates and numbers.
 */
@Component
public class InvoiceExport {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** The columns, in order: header and how to read the value from an invoice. */
    private record Column(String header, int width, Function<Invoice, Object> value) {
    }

    private static final List<Column> COLUMNS = List.of(
        new Column("N° facture", 14, i -> i.getInvoiceNumber() != null ? i.getInvoiceNumber() : "Brouillon"),
        new Column("Statut", 11, i -> statusLabel(i.getStatus())),
        new Column("Date d'émission", 14, Invoice::getIssueDate),
        new Column("Échéance", 12, Invoice::getDueDate),
        // Issued invoices: the client as frozen on the invoice; drafts: the current client
        new Column("Client", 32, i -> i.getClientName() != null ? i.getClientName() : i.getClient().getName()),
        new Column("NIF client", 22, i -> i.isDraft() ? i.getClient().getNif() : i.getClientNif()),
        new Column("Mode de paiement", 18, i -> paymentLabel(i.getPaymentMethod())),
        // Credit notes negative, so that a column sum is the net amount
        new Column("Total HT (DA)", 16, i -> signed(i, i.getSubtotalAmount())),
        new Column("TVA (DA)", 14, i -> signed(i, i.getVatAmount())),
        new Column("Total TTC (DA)", 16, i -> signed(i, i.getTotalAmount())),
        new Column("Émise le", 17, Invoice::getIssuedAt),
        new Column("Payée le", 17, Invoice::getPaidAt),
        new Column("Annulée le", 17, Invoice::getCancelledAt),
        new Column("Type", 10, i -> i.getKind().getLabel()),
        new Column("Facture d'origine", 16, i -> i.getOriginalInvoice() != null ? i.getOriginalInvoice().getInvoiceNumber() : null),
        new Column("Droit de timbre (DA)", 16, Invoice::getStampDutyAmount));

    public byte[] toCsv(List<Invoice> invoices) {
        StringBuilder csv = new StringBuilder("﻿");
        appendCsvRow(csv, COLUMNS.stream().map(Column::header).map(Object.class::cast).toList());
        for (Invoice invoice : invoices) {
            appendCsvRow(csv, COLUMNS.stream().map(c -> c.value().apply(invoice)).toList());
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] toXlsx(List<Invoice> invoices) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Factures");
            CellStyle header = headerStyle(workbook);
            CellStyle date = formatStyle(workbook, "dd/mm/yyyy");
            CellStyle dateTime = formatStyle(workbook, "dd/mm/yyyy hh:mm");
            CellStyle amount = formatStyle(workbook, "#,##0.00");

            Row headerRow = sheet.createRow(0);
            for (int c = 0; c < COLUMNS.size(); c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(COLUMNS.get(c).header());
                cell.setCellStyle(header);
                sheet.setColumnWidth(c, COLUMNS.get(c).width() * 256);
            }
            int r = 1;
            for (Invoice invoice : invoices) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < COLUMNS.size(); c++) {
                    Object value = COLUMNS.get(c).value().apply(invoice);
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
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(r - 1, 0), 0, COLUMNS.size() - 1));
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

    static String statusLabel(InvoiceStatus status) {
        return switch (status) {
            case DRAFT -> "Brouillon";
            case ISSUED -> "Émise";
            case PAID -> "Payée";
            case CANCELLED -> "Annulée";
            case ACCEPTED -> "Accepté";
            case REFUSED -> "Refusé";
        };
    }

    private static String paymentLabel(PaymentMethod method) {
        if (method == null) {
            return null;
        }
        return switch (method) {
            case CASH -> "Espèces";
            case BANK_TRANSFER -> "Virement bancaire";
            case CHEQUE -> "Chèque";
            case CCP -> "Virement CCP";
            case OTHER -> "Autre";
        };
    }
}
