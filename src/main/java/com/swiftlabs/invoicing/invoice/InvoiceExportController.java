package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.common.Privileges;

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
import java.util.List;

/** Downloads of the invoice register (invoices and credit notes), with the same filters as the list page. */
@Controller
@RequestMapping(Endpoints.INVOICE_EXPORT)
@PreAuthorize(Privileges.Has.VIEW_INVOICES)
public class InvoiceExportController {

    /** Chronological, as in the register. */
    private static final Sort EXPORT_ORDER = Sort.by("issueDate", "sequenceNumber", "id");

    private static final MediaType XLSX =
        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final InvoiceService invoiceService;
    private final InvoiceExport export;
    private final PaymentService paymentService;
    private final Clock clock;
    private final Messages messages;

    public InvoiceExportController(InvoiceService invoiceService, InvoiceExport export, PaymentService paymentService, Clock clock,
                                   Messages messages) {
        this.paymentService = paymentService;
        this.invoiceService = invoiceService;
        this.export = export;
        this.clock = clock;
        this.messages = messages;
    }

    @GetMapping(Endpoints.INVOICE_EXPORT_CSV)
    public ResponseEntity<byte[]> csv(@ModelAttribute(CommonCodes.Attributes.FILTER) InvoiceFilter filter, BindingResult ignoredFilterErrors) {
        filter.setKinds(DocumentKind.ACCOUNTING);
        List<Invoice> invoices = invoiceService.searchAll(filter, EXPORT_ORDER);
        byte[] body = export.toCsv(invoices, paymentService.summaries(invoices));
        return download(body, new MediaType("text", "csv", StandardCharsets.UTF_8), fileName(filter, "csv"));
    }

    @GetMapping(Endpoints.INVOICE_EXPORT_XLSX)
    public ResponseEntity<byte[]> xlsx(@ModelAttribute(CommonCodes.Attributes.FILTER) InvoiceFilter filter, BindingResult ignoredFilterErrors) {
        filter.setKinds(DocumentKind.ACCOUNTING);
        List<Invoice> invoices = invoiceService.searchAll(filter, EXPORT_ORDER);
        byte[] body = export.toXlsx(invoices, paymentService.summaries(invoices));
        return download(body, XLSX, fileName(filter, "xlsx"));
    }

    private static ResponseEntity<byte[]> download(byte[] body, MediaType type, String fileName) {
        return ResponseEntity.ok()
            .contentType(type)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
            .body(body);
    }

    /** e.g. factures-2026-export-2026-10-24.xlsx (invoices-... in English: the prefix is ASCII in every language) */
    private String fileName(InvoiceFilter filter, String extension) {
        String year = filter.getYear() != null ? "-" + filter.getYear() : "";
        return messages.get(MessageProperties.INVOICE_EXPORT_FILE_NAME) + year + "-export-" + LocalDate.now(clock) + "." + extension;
    }
}
