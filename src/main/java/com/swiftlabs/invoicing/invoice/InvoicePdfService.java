package com.swiftlabs.invoicing.invoice;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.swiftlabs.invoicing.common.AmountInWords;
import com.swiftlabs.invoicing.common.AmountUtils;
import com.swiftlabs.invoicing.config.InvoiceProperties;
import com.swiftlabs.invoicing.issuer.IssuerService;

import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Renders the invoice/pdf Thymeleaf template to a PDF document. */
@Service
public class InvoicePdfService {

    private final SpringTemplateEngine templateEngine;
    private final IssuerService issuerService;
    private final InvoiceProperties properties;

    public InvoicePdfService(SpringTemplateEngine templateEngine, IssuerService issuerService,
                             InvoiceProperties properties) {
        this.templateEngine = templateEngine;
        this.issuerService = issuerService;
        this.properties = properties;
    }

    /** The invoice must have its lines loaded (see {@link InvoiceService#findWithLines}). */
    public byte[] render(Invoice invoice) throws IOException {
        Context context = new Context();
        context.setVariable("invoice", invoice);
        context.setVariable("vatMention", properties.vatMention());
        // Drafts have no snapshot yet: the template falls back to the live issuer
        context.setVariable("liveIssuer", issuerService.getIssuer());
        context.setVariable("amountUtils", new AmountUtils());
        context.setVariable("amountInWords", new AmountInWords());

        String html = templateEngine.process("invoice/pdf", context);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(html, "file:///");
        builder.toStream(out);
        builder.run();
        return out.toByteArray();
    }
}
