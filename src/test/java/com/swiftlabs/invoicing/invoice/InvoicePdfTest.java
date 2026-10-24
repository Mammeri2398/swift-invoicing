package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;
import com.swiftlabs.invoicing.issuer.Issuer;
import com.swiftlabs.invoicing.issuer.IssuerService;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Content of the generated PDF, read back with PDFBox. */
@SpringBootTest
@Transactional
class InvoicePdfTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoicePdfService pdfService;

    @Autowired
    private IssuerService issuerService;

    @Autowired
    private ClientRepository clientRepository;

    private Client client;

    @BeforeEach
    void data() {
        Issuer issuer = issuerService.getIssuer();
        issuer.setFullName("Karim Benali");
        issuer.setAddress("12 rue Didouche Mourad");
        issuer.setCity("Alger");
        issuer.setPhone("0555 12 34 56");
        issuer.setEmail("karim@example.dz");
        issuer.setBankOrCcpAccount("CCP 0021458992 clé 45");

        client = new Client();
        client.setType(ClientType.PARTICULIER);
        client.setName("Client Test");
        client.setNif("");
        client = clientRepository.save(client);
    }

    private Invoice draft() {
        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription("Développement");
        line.setUnit("jour");
        line.setQuantity(new BigDecimal("2"));
        line.setUnitPrice(new BigDecimal("15000.00"));
        InvoiceForm form = new InvoiceForm();
        form.setClientId(client.getId());
        form.setIssueDate(LocalDate.of(2026, 5, 4));
        form.setPaymentMethod("BANK_TRANSFER");
        form.setLines(List.of(line));
        return invoiceService.saveDraft(form);
    }

    private PDDocument pdf(Invoice invoice) throws IOException {
        return PDDocument.load(pdfService.render(invoiceService.findWithLines(invoice.getId())));
    }

    private String text(Invoice invoice) throws IOException {
        try (PDDocument document = pdf(invoice)) {
            // Visual order: the renderer does not write text runs in reading order
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private static int occurrences(String text, String part) {
        return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
    }

    @Test
    void issuingSnapshotsCityPhoneAndEmail() {
        Invoice issued = invoiceService.issue(draft().getId());
        assertThat(issued.getIssuerCity()).isEqualTo("Alger");
        assertThat(issued.getIssuerPhone()).isEqualTo("0555 12 34 56");
        assertThat(issued.getIssuerEmail()).isEqualTo("karim@example.dz");
    }

    @Test
    void headerPrintsAddressAndCityOnce() throws IOException {
        String text = text(invoiceService.issue(draft().getId()));
        assertThat(text).contains("12 rue Didouche Mourad, Alger");
        assertThat(occurrences(text, "12 rue Didouche Mourad")).isEqualTo(1);
        assertThat(text).contains("0555 12 34 56").contains("karim@example.dz");
    }

    @Test
    void issuedInvoiceKeepsTheIssuerDetailsOfItsIssueDate() throws IOException {
        Invoice issued = invoiceService.issue(draft().getId());
        Invoice laterDraft = draft();

        Issuer issuer = issuerService.getIssuer();
        issuer.setCity("Oran");
        issuer.setPhone("0661 00 00 00");

        String issuedText = text(issued);
        assertThat(issuedText).contains("Alger").contains("0555 12 34 56").doesNotContain("Oran");
        // A draft is not frozen yet: it shows the current profile
        assertThat(text(laterDraft)).contains("Oran").contains("0661 00 00 00");
    }

    @Test
    void linesPaymentAndAccentsAreRendered() throws IOException {
        String text = text(invoiceService.issue(draft().getId()));
        assertThat(text).contains("Développement").contains("jour").contains("30 000,00 DA");
        assertThat(text).contains("Date d'émission : 04/05/2026");
        assertThat(text).contains("Virement bancaire").contains("CCP 0021458992 clé 45");
        // Client without NIF (empty string): only the issuer's NIF is printed
        assertThat(occurrences(text, "NIF :")).isEqualTo(1);
    }

    @Test
    void logoIsEmbeddedWhenSet() throws IOException {
        Invoice invoice = draft();
        try (PDDocument document = pdf(invoice)) {
            assertThat(images(document)).isZero();
        }

        issuerService.updateLogo(png(120, 60));
        try (PDDocument document = pdf(invoice)) {
            assertThat(images(document)).isEqualTo(1);
        }
    }

    private static int images(PDDocument document) throws IOException {
        PDResources resources = document.getPage(0).getResources();
        int count = 0;
        for (COSName name : resources.getXObjectNames()) {
            if (resources.getXObject(name) instanceof PDImageXObject) {
                count++;
            }
        }
        return count;
    }

    static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.getGraphics().fillRect(0, 0, width / 2, height / 2);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
