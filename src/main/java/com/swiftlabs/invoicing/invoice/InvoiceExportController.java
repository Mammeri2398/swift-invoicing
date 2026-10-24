package com.swiftlabs.invoicing.invoice;

import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;

/** Downloads of the invoice list, with the same filters as the list page. */
@Controller
@RequestMapping("/invoices/export")
@PreAuthorize("hasAuthority('VIEW_INVOICES')")
public class InvoiceExportController {

    /** Chronological, as in the register. */
    private static final Sort EXPORT_ORDER = Sort.by("issueDate", "sequenceNumber", "id");

    private static final MediaType XLSX =
        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final InvoiceService invoiceService;
    private final InvoiceExport export;
    private final Clock clock;

    public InvoiceExportController(InvoiceService invoiceService, InvoiceExport export, Clock clock) {
        this.invoiceService = invoiceService;
        this.export = export;
        this.clock = clock;
    }

    @GetMapping("/csv")
    public ResponseEntity<byte[]> csv(@ModelAttribute("filter") InvoiceFilter filter, BindingResult ignoredFilterErrors) {
        byte[] body = export.toCsv(invoiceService.searchAll(filter, EXPORT_ORDER));
        return download(body, new MediaType("text", "csv", StandardCharsets.UTF_8), fileName(filter, "csv"));
    }

    @GetMapping("/xlsx")
    public ResponseEntity<byte[]> xlsx(@ModelAttribute("filter") InvoiceFilter filter, BindingResult ignoredFilterErrors) {
        byte[] body = export.toXlsx(invoiceService.searchAll(filter, EXPORT_ORDER));
        return download(body, XLSX, fileName(filter, "xlsx"));
    }

    private static ResponseEntity<byte[]> download(byte[] body, MediaType type, String fileName) {
        return ResponseEntity.ok()
            .contentType(type)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
            .body(body);
    }

    /** e.g. factures-2026-export-2026-10-24.xlsx */
    private String fileName(InvoiceFilter filter, String extension) {
        String year = filter.getYear() != null ? "-" + filter.getYear() : "";
        return "factures" + year + "-export-" + LocalDate.now(clock) + "." + extension;
    }
}
