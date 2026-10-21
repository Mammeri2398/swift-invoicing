package com.swiftlabs.invoicing.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.swiftlabs.invoicing.domain.Client;
import com.swiftlabs.invoicing.domain.Invoice;
import com.swiftlabs.invoicing.domain.Issuer;
import com.swiftlabs.invoicing.util.AmountInWords;
import org.springframework.stereotype.Service;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class PdfService {

    private final SpringTemplateEngine templateEngine;
    private final IssuerService issuerService;
    private final ClientService clientService;

    public PdfService(SpringTemplateEngine templateEngine, IssuerService issuerService, ClientService clientService) {
        this.templateEngine = templateEngine;
        this.issuerService = issuerService;
        this.clientService = clientService;
    }

    public byte[] renderInvoicePdf(Invoice invoice) throws IOException {
        Context context = new Context();
        context.setVariable("invoice", invoice);
        context.setVariable("vatMention", issuerService.getVatMention());
        context.setVariable("liveIssuer", issuerService.getIssuer());
        context.setVariable("liveClient", invoice.getClient());
        context.setVariable("formatAmount", new AmountFormatter());
        context.setVariable("amountInWords", new AmountInWords());

        String html = templateEngine.process("invoices/pdf", context);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(html, "file:///");
        builder.toStream(baos);
        builder.run();
        return baos.toByteArray();
    }

    public static class AmountFormatter {
        public String format(java.math.BigDecimal amount) {
            return com.swiftlabs.invoicing.util.AmountUtils.format(amount);
        }
    }
}
