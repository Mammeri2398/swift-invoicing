package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.common.AmountInWords;
import com.swiftlabs.invoicing.common.AmountUtils;
import com.swiftlabs.invoicing.config.InvoiceProperties;
import com.swiftlabs.invoicing.issuer.Issuer;
import com.swiftlabs.invoicing.issuer.IssuerService;

import com.openhtmltopdf.bidi.support.ICUBidiReorderer;
import com.openhtmltopdf.bidi.support.ICUBidiSplitter;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.TextDirection;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.slf4j.Slf4jLogger;
import com.openhtmltopdf.util.XRLog;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Locale;

/** Renders the invoice/pdf Thymeleaf template to a PDF document. */
@Service
public class InvoicePdfService {

    /**
     * Embedded font (subset): covers Latin with accents and Arabic, so client names and notes in
     * Arabic print correctly. The built-in PDF fonts only know Western European characters.
     */
    static final String FONT_FAMILY = "DejaVu Sans";
    private static final String FONT_REGULAR = "/pdf/fonts/DejaVuSans.ttf";
    private static final String FONT_BOLD = "/pdf/fonts/DejaVuSans-Bold.ttf";

    static {
        // Through SLF4J, so logging.level.com.openhtmltopdf applies (the library otherwise prints to the console)
        XRLog.setLoggerImpl(new Slf4jLogger());
    }

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
        Issuer issuer = issuerService.getIssuer();
        // A document for the client: always in French, whatever the language of the user who prints it
        Context context = new Context(Locale.FRENCH);
        context.setVariable("invoice", invoice);
        context.setVariable("vatMention", properties.vatMention());
        context.setVariable("issuer", IssuerDetails.of(invoice, issuer));
        // Embedded in the document: no URL to resolve while rendering
        context.setVariable("logoDataUri", issuer.hasLogo()
            ? "data:" + issuer.getLogoContentType() + ";base64," + Base64.getEncoder().encodeToString(issuer.getLogoData())
            : null);
        context.setVariable("amountUtils", new AmountUtils());
        context.setVariable("amountInWords", new AmountInWords());

        String html = templateEngine.process("invoice/pdf", context);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.useFont(() -> InvoicePdfService.class.getResourceAsStream(FONT_REGULAR), FONT_FAMILY, 400, FontStyle.NORMAL, true);
        // The template only uses normal and bold: a weight without its own face would be faked
        builder.useFont(() -> InvoicePdfService.class.getResourceAsStream(FONT_BOLD), FONT_FAMILY, 700, FontStyle.NORMAL, true);
        // Arabic: split the text into left-to-right and right-to-left runs, reorder and join the letters
        builder.useUnicodeBidiSplitter(new ICUBidiSplitter.ICUBidiSplitterFactory());
        builder.useUnicodeBidiReorderer(new ICUBidiReorderer());
        builder.defaultTextDirection(TextDirection.LTR);
        builder.withHtmlContent(html, "file:///");
        builder.toStream(out);
        builder.run();
        return out.toByteArray();
    }
}
