package com.swiftlabs.invoicing.accounting;

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
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The accounting of a period for the accountant: an Excel workbook (entries, sales, payments, accounts used) and the
 * entries alone as CSV, to import into accounting software (";" separator, decimal comma, UTF-8 with BOM).
 */
@Component
public class AccountingExport {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final List<String> ENTRY_COLUMNS = List.of(MessageProperties.ACCOUNTING_COL_JOURNAL,
        MessageProperties.ACCOUNTING_COL_DATE, MessageProperties.ACCOUNTING_COL_PIECE, MessageProperties.ACCOUNTING_COL_ACCOUNT,
        MessageProperties.ACCOUNTING_COL_PARTY, MessageProperties.ACCOUNTING_COL_LABEL, MessageProperties.ACCOUNTING_COL_DEBIT,
        MessageProperties.ACCOUNTING_COL_CREDIT);

    private final Messages messages;

    public AccountingExport(Messages messages) {
        this.messages = messages;
    }

    public byte[] toCsv(AccountingJournal journal) {
        StringBuilder csv = new StringBuilder("﻿");
        appendCsvRow(csv, ENTRY_COLUMNS.stream().map(key -> (Object) messages.get(key)).toList());
        for (AccountingJournal.Entry entry : journal.entries()) {
            appendCsvRow(csv, List.of(entry.journal(), entry.date(), entry.piece(), entry.account(), entry.party(), entry.label(),
                entry.debit(), entry.credit()));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] toXlsx(AccountingJournal journal) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            String period = messages.get(MessageProperties.ACCOUNTING_PERIOD, journal.from().format(DATE), journal.to().format(DATE));
            entries(workbook, styles, journal, period);
            sales(workbook, styles, journal, period);
            receipts(workbook, styles, journal, period);
            accounts(workbook, styles, journal.accounts());
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void entries(XSSFWorkbook workbook, Styles styles, AccountingJournal journal, String period) {
        Sheet sheet = sheet(workbook, MessageProperties.ACCOUNTING_EXPORT_SHEET_ENTRIES, period, styles,
            ENTRY_COLUMNS, List.of(9, 12, 16, 12, 30, 52, 16, 16));
        int r = 3;
        for (AccountingJournal.Entry entry : journal.entries()) {
            Row row = sheet.createRow(r++);
            text(row, 0, entry.journal(), null);
            date(row, 1, entry.date(), styles.date);
            text(row, 2, entry.piece(), null);
            text(row, 3, entry.account(), null);
            text(row, 4, entry.party(), null);
            text(row, 5, entry.label(), null);
            amount(row, 6, entry.debit(), styles.amount);
            amount(row, 7, entry.credit(), styles.amount);
        }
        Row total = sheet.createRow(r);
        text(total, 5, messages.get(MessageProperties.ACCOUNTING_TOTAL), styles.bold);
        amount(total, 6, journal.totalDebit(), styles.boldAmount);
        amount(total, 7, journal.totalCredit(), styles.boldAmount);
    }

    private void sales(XSSFWorkbook workbook, Styles styles, AccountingJournal journal, String period) {
        Sheet sheet = sheet(workbook, MessageProperties.ACCOUNTING_EXPORT_SHEET_SALES, period, styles,
            List.of(MessageProperties.ACCOUNTING_COL_DATE, MessageProperties.ACCOUNTING_COL_NUMBER, MessageProperties.ACCOUNTING_COL_KIND,
                MessageProperties.ACCOUNTING_COL_CLIENT, MessageProperties.ACCOUNTING_COL_NIF, MessageProperties.ACCOUNTING_COL_ORIGINAL,
                MessageProperties.ACCOUNTING_COL_EXCL_VAT, MessageProperties.ACCOUNTING_COL_VAT, MessageProperties.ACCOUNTING_COL_STAMP,
                MessageProperties.ACCOUNTING_COL_TOTAL),
            List.of(12, 16, 14, 30, 22, 16, 16, 14, 12, 16));
        int r = 3;
        BigDecimal[] totals = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        for (AccountingJournal.Sale sale : journal.sales()) {
            Row row = sheet.createRow(r++);
            date(row, 0, sale.date(), styles.date);
            text(row, 1, sale.number(), null);
            text(row, 2, sale.cancellation() ? messages.get(MessageProperties.ACCOUNTING_KIND_CANCELLATION) : messages.label(sale.kind()), null);
            text(row, 3, sale.client(), null);
            text(row, 4, sale.clientNif(), null);
            text(row, 5, sale.originalInvoice(), null);
            BigDecimal[] values = {sale.exclVat(), sale.vat(), sale.stampDuty(), sale.total()};
            for (int i = 0; i < values.length; i++) {
                amount(row, 6 + i, values[i], styles.amount);
                totals[i] = totals[i].add(values[i]);
            }
        }
        Row total = sheet.createRow(r);
        text(total, 5, messages.get(MessageProperties.ACCOUNTING_TOTAL), styles.bold);
        for (int i = 0; i < totals.length; i++) {
            amount(total, 6 + i, totals[i], styles.boldAmount);
        }
    }

    private void receipts(XSSFWorkbook workbook, Styles styles, AccountingJournal journal, String period) {
        Sheet sheet = sheet(workbook, MessageProperties.ACCOUNTING_EXPORT_SHEET_RECEIPTS, period, styles,
            List.of(MessageProperties.ACCOUNTING_COL_DATE, MessageProperties.ACCOUNTING_COL_NUMBER, MessageProperties.ACCOUNTING_COL_CLIENT,
                MessageProperties.ACCOUNTING_COL_METHOD, MessageProperties.ACCOUNTING_COL_REFERENCE, MessageProperties.ACCOUNTING_COL_JOURNAL,
                MessageProperties.ACCOUNTING_COL_AMOUNT),
            List.of(12, 16, 30, 18, 22, 9, 16));
        int r = 3;
        for (AccountingJournal.Receipt receipt : journal.receipts()) {
            Row row = sheet.createRow(r++);
            date(row, 0, receipt.date(), styles.date);
            text(row, 1, receipt.number(), null);
            text(row, 2, receipt.client(), null);
            text(row, 3, receipt.method() != null ? messages.label(receipt.method()) : null, null);
            text(row, 4, receipt.reference(), null);
            text(row, 5, receipt.journal(), null);
            amount(row, 6, receipt.amount(), styles.amount);
        }
        Row total = sheet.createRow(r);
        text(total, 5, messages.get(MessageProperties.ACCOUNTING_TOTAL), styles.bold);
        amount(total, 6, journal.receiptsTotal(), styles.boldAmount);
    }

    /** The accounts and journals the entries were written with, so the accountant can check them. */
    private void accounts(XSSFWorkbook workbook, Styles styles, AccountingSettings accounts) {
        Sheet sheet = workbook.createSheet(WorkbookUtil.createSafeSheetName(messages.get(MessageProperties.ACCOUNTING_EXPORT_SHEET_ACCOUNTS)));
        String[][] rows = {
            {MessageProperties.ACCOUNTING_ACCOUNT_CUSTOMER, accounts.getCustomerAccount()},
            {MessageProperties.ACCOUNTING_ACCOUNT_SALES, accounts.getSalesAccount()},
            {MessageProperties.ACCOUNTING_ACCOUNT_VAT, accounts.getVatAccount()},
            {MessageProperties.ACCOUNTING_ACCOUNT_STAMP, accounts.getStampAccount()},
            {MessageProperties.ACCOUNTING_ACCOUNT_BANK, accounts.getBankAccount()},
            {MessageProperties.ACCOUNTING_ACCOUNT_CASH, accounts.getCashAccount()},
            {MessageProperties.ACCOUNTING_JOURNAL_SALES, accounts.getSalesJournal()},
            {MessageProperties.ACCOUNTING_JOURNAL_BANK, accounts.getBankJournal()},
            {MessageProperties.ACCOUNTING_JOURNAL_CASH, accounts.getCashJournal()},
        };
        for (int r = 0; r < rows.length; r++) {
            Row row = sheet.createRow(r);
            text(row, 0, messages.get(rows[r][0]), styles.bold);
            text(row, 1, rows[r][1], null);
        }
        sheet.setColumnWidth(0, 36 * 256);
        sheet.setColumnWidth(1, 14 * 256);
    }

    /** A sheet with its title (row 1), the period (row 2) and the column headings (row 4), frozen below them. */
    private Sheet sheet(XSSFWorkbook workbook, String nameKey, String period, Styles styles, List<String> columns, List<Integer> widths) {
        Sheet sheet = workbook.createSheet(WorkbookUtil.createSafeSheetName(messages.get(nameKey)));
        text(sheet.createRow(0), 0, messages.get(nameKey), styles.bold);
        text(sheet.createRow(1), 0, period, null);
        Row headings = sheet.createRow(2);
        for (int c = 0; c < columns.size(); c++) {
            text(headings, c, messages.get(columns.get(c)), styles.header);
            sheet.setColumnWidth(c, widths.get(c) * 256);
        }
        sheet.createFreezePane(0, 3);
        return sheet;
    }

    /** Text is always a string cell: never interpreted as a formula. */
    private static void text(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private static void amount(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }

    private static void date(Row row, int column, LocalDate value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static final class Styles {

        final CellStyle header;
        final CellStyle bold;
        final CellStyle amount;
        final CellStyle boldAmount;
        final CellStyle date;

        Styles(XSSFWorkbook workbook) {
            Font font = workbook.createFont();
            font.setBold(true);
            bold = workbook.createCellStyle();
            bold.setFont(font);
            header = workbook.createCellStyle();
            header.setFont(font);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);
            amount = workbook.createCellStyle();
            amount.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
            boldAmount = workbook.createCellStyle();
            boldAmount.setFont(font);
            boldAmount.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
            date = workbook.createCellStyle();
            date.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy"));
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
        if (value instanceof BigDecimal n) {
            return n.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
        }
        return csvText(value.toString());
    }

    /** Quotes text when needed, and neutralises a leading =, +, - or @ (CSV injection), as InvoiceExport does. */
    private static String csvText(String text) {
        String safe = !text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0 ? "'" + text : text;
        if (safe.contains(";") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
