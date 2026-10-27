package com.swiftlabs.invoicing.vat;

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
import java.util.List;

/** The month's TVA summary as an Excel workbook: the summary sheet (G50 layout) and the detail sheet. */
@Component
public class VatExport {

    private final Messages messages;

    public VatExport(Messages messages) {
        this.messages = messages;
    }

    public byte[] toXlsx(VatSummary summary, String periodLabel) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(workbook);
            CellStyle bold = boldStyle(workbook);
            CellStyle amount = formatStyle(workbook, "#,##0.00", false);
            CellStyle boldAmount = formatStyle(workbook, "#,##0.00", true);
            CellStyle date = formatStyle(workbook, "dd/mm/yyyy", false);

            Sheet sheet = workbook.createSheet(WorkbookUtil.createSafeSheetName(messages.get(MessageProperties.VAT_EXPORT_SHEET_SUMMARY)));
            int r = 0;
            text(sheet.createRow(r++), 0, messages.get(MessageProperties.VAT_SUMMARY_TITLE, periodLabel), bold);
            text(sheet.createRow(r++), 0, messages.get(MessageProperties.VAT_BASIS) + " : "
                + messages.get(MessageProperties.VAT_BASIS_PREFIX + summary.basis().name()), null);
            r++;
            Row headings = sheet.createRow(r++);
            text(headings, 0, messages.get(MessageProperties.VAT_COL_OPERATION), header);
            text(headings, 1, messages.get(MessageProperties.VAT_COL_BASE), header);
            text(headings, 2, messages.get(MessageProperties.VAT_COL_VAT), header);
            for (VatSummary.Rate rate : summary.rates()) {
                Row row = sheet.createRow(r++);
                text(row, 0, messages.get(MessageProperties.VAT_RATE, rate.rate().stripTrailingZeros().toPlainString()), null);
                number(row, 1, rate.base(), amount);
                number(row, 2, rate.vat(), amount);
            }
            Row exempt = sheet.createRow(r++);
            text(exempt, 0, messages.get(MessageProperties.VAT_EXEMPT), null);
            number(exempt, 1, summary.exemptBase(), amount);
            Row total = sheet.createRow(r++);
            text(total, 0, messages.get(MessageProperties.VAT_TOTAL), bold);
            number(total, 1, summary.exclVat(), boldAmount);
            number(total, 2, summary.vat(), boldAmount);
            r++;
            Row stamp = sheet.createRow(r++);
            text(stamp, 0, messages.get(MessageProperties.VAT_STAMP_DUTY), null);
            number(stamp, 1, summary.stampDuty(), amount);
            Row inclVat = sheet.createRow(r);
            text(inclVat, 0, messages.get(MessageProperties.VAT_TOTAL_INCL_VAT), null);
            number(inclVat, 1, summary.inclVat(), amount);
            sheet.setColumnWidth(0, 48 * 256);
            sheet.setColumnWidth(1, 20 * 256);
            sheet.setColumnWidth(2, 16 * 256);

            Sheet details = workbook.createSheet(WorkbookUtil.createSafeSheetName(messages.get(MessageProperties.VAT_EXPORT_SHEET_DETAILS)));
            List<String> columns = List.of(MessageProperties.VAT_DETAILS_DATE, MessageProperties.VAT_DETAILS_DOCUMENT,
                MessageProperties.VAT_DETAILS_CLIENT, MessageProperties.VAT_DETAILS_EXCL_VAT, MessageProperties.VAT_DETAILS_VAT,
                MessageProperties.VAT_DETAILS_STAMP, MessageProperties.VAT_DETAILS_TOTAL);
            Row detailHeadings = details.createRow(0);
            for (int c = 0; c < columns.size(); c++) {
                text(detailHeadings, c, messages.get(columns.get(c)), header);
                details.setColumnWidth(c, (c == 2 ? 34 : 16) * 256);
            }
            int d = 1;
            for (VatSummary.Entry entry : summary.entries()) {
                Row row = details.createRow(d++);
                Cell cell = row.createCell(0);
                cell.setCellValue(entry.date());
                cell.setCellStyle(date);
                text(row, 1, entry.number(), null);
                text(row, 2, entry.client(), null);
                number(row, 3, entry.exclVat(), amount);
                number(row, 4, entry.vat(), amount);
                number(row, 5, entry.stampDuty(), amount);
                number(row, 6, entry.total(), amount);
            }
            details.createFreezePane(0, 1);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** Text is always a string cell: never interpreted as a formula. */
    private static void text(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private static void number(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }

    private static CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = boldStyle(workbook);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private static CellStyle boldStyle(XSSFWorkbook workbook) {
        Font bold = workbook.createFont();
        bold.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(bold);
        return style;
    }

    private static CellStyle formatStyle(XSSFWorkbook workbook, String format, boolean bold) {
        CellStyle style = bold ? boldStyle(workbook) : workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat(format));
        return style;
    }
}
