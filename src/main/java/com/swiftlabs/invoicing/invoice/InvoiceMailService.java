package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.common.AmountUtils;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.mail.MailGateway;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

/**
 * Sends an issued invoice to the client by email, with its PDF attached, and records it in the history.
 * The proposed subject and message are written to the client, hence always in French; errors are keys.
 */
@Service
@Transactional(readOnly = true)
public class InvoiceMailService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceMailService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final InvoiceService invoiceService;
    private final InvoicePdfService pdfService;
    private final InvoiceAudit audit;
    private final PaymentService paymentService;
    private final MailGateway mailGateway;

    public InvoiceMailService(InvoiceService invoiceService, InvoicePdfService pdfService, InvoiceAudit audit,
                              PaymentService paymentService, MailGateway mailGateway) {
        this.invoiceService = invoiceService;
        this.pdfService = pdfService;
        this.audit = audit;
        this.paymentService = paymentService;
        this.mailGateway = mailGateway;
    }

    /** False when no mail server is configured: the page then explains how to set one up. */
    public boolean isEnabled() {
        return mailGateway.isEnabled();
    }

    public String getFromAddress() {
        return mailGateway.fromAddress();
    }

    /** The email proposed for an invoice: the client's address and a ready-to-send message. */
    public InvoiceEmailForm prepare(Long invoiceId) {
        Invoice invoice = sendable(invoiceId);
        InvoiceEmailForm form = new InvoiceEmailForm();
        form.setTo(invoice.getClient().getEmail());
        form.setSubject(documentName(invoice) + " " + invoice.getInvoiceNumber() + " - " + senderName(invoice));
        form.setMessage(defaultMessage(invoice));
        return form;
    }

    /** A payment reminder: what is left to pay on an issued invoice and its due date. */
    public InvoiceEmailForm prepareReminder(Long invoiceId) {
        Invoice invoice = sendable(invoiceId);
        BigDecimal balance = paymentService.balance(invoice);
        if (invoice.getKind() != DocumentKind.INVOICE || invoice.getStatus() != InvoiceStatus.ISSUED || balance.signum() <= 0) {
            throw new BusinessException("invoiceMail.error.nothingToPay");
        }
        InvoiceEmailForm form = new InvoiceEmailForm();
        form.setReminder(true);
        form.setTo(invoice.getClient().getEmail());
        form.setSubject("Relance : facture " + invoice.getInvoiceNumber() + " - " + senderName(invoice));
        StringBuilder text = new StringBuilder("Bonjour,\n\n")
            .append("Sauf erreur de notre part, la facture ").append(invoice.getInvoiceNumber())
            .append(" du ").append(invoice.getIssueDate().format(DATE));
        if (invoice.getDueDate() != null) {
            text.append(", échue le ").append(invoice.getDueDate().format(DATE)).append(",");
        }
        text.append(" reste à régler pour un montant de ").append(AmountUtils.format(balance)).append(".\n");
        if (invoice.getIssuerBankAccount() != null && !invoice.getIssuerBankAccount().isBlank()) {
            text.append("Règlement : ").append(invoice.getIssuerBankAccount()).append("\n");
        }
        text.append("\nSi le paiement a déjà été effectué, merci de ne pas tenir compte de ce message.\n")
            .append("\nCordialement,\n").append(invoice.getIssuerFullName());
        form.setMessage(text.toString());
        return form;
    }

    @Transactional
    public void send(Long invoiceId, InvoiceEmailForm form) {
        Invoice invoice = sendable(invoiceId);
        if (!isEnabled()) {
            throw new BusinessException("invoiceMail.error.disabled");
        }
        try {
            JavaMailSender mailSender = mailGateway.sender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(mailGateway.fromAddress(), senderName(invoice), StandardCharsets.UTF_8.name()));
            // Replies go to the auto-entrepreneur, not to the sending account
            if (invoice.getIssuerEmail() != null && !invoice.getIssuerEmail().isBlank()) {
                helper.setReplyTo(invoice.getIssuerEmail());
            }
            helper.setTo(form.getTo());
            if (form.hasCc()) {
                helper.setCc(form.getCc());
            }
            helper.setSubject(form.getSubject());
            helper.setText(form.getMessage());
            helper.addAttachment(invoice.getInvoiceNumber() + ".pdf",
                new ByteArrayResource(pdfService.render(invoiceService.findWithLines(invoiceId))), "application/pdf");
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw BusinessException.onField("to", "invoiceMail.error.invalidAddress");
        } catch (MailException | IOException ex) {
            log.error("Could not send invoice {} by email", invoice.getInvoiceNumber(), ex);
            throw new BusinessException("invoiceMail.error.sendFailed");
        }
        audit.record(invoice, form.isReminder() ? InvoiceEvent.Action.REMINDED : InvoiceEvent.Action.SENT,
            form.hasCc() ? form.getTo() + ", cc " + form.getCc() : form.getTo());
    }

    /** Only issued and paid invoices are sent: a draft has no number, a cancelled invoice is void. */
    private Invoice sendable(Long invoiceId) {
        Invoice invoice = invoiceService.findById(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.ISSUED && invoice.getStatus() != InvoiceStatus.PAID
                && invoice.getStatus() != InvoiceStatus.ACCEPTED) {
            throw new BusinessException("invoiceMail.error.notSendable");
        }
        return invoice;
    }

    private static String senderName(Invoice invoice) {
        return invoice.getIssuerTradeName() != null && !invoice.getIssuerTradeName().isBlank()
            ? invoice.getIssuerTradeName() : invoice.getIssuerFullName();
    }

    private static String documentName(Invoice invoice) {
        return switch (invoice.getKind()) {
            case INVOICE -> "Facture";
            case CREDIT_NOTE -> "Avoir";
            case QUOTE -> "Devis";
            case PROFORMA -> "Facture pro forma";
        };
    }

    private static String defaultMessage(Invoice invoice) {
        if (invoice.isOffer()) {
            StringBuilder text = new StringBuilder("Bonjour,\n\nVeuillez trouver ci-joint ")
                .append(invoice.isQuote() ? "notre devis " : "la facture pro forma ").append(invoice.getInvoiceNumber())
                .append(" du ").append(invoice.getIssueDate().format(DATE))
                .append(", d'un montant de ").append(AmountUtils.format(invoice.getTotalAmount()));
            if (invoice.isQuote() && invoice.getDueDate() != null) {
                text.append(", valable jusqu'au ").append(invoice.getDueDate().format(DATE));
            }
            return text.append(".\nNous restons à votre disposition pour toute question.\n\nCordialement,\n")
                .append(invoice.getIssuerFullName()).toString();
        }
        if (invoice.isCreditNote()) {
            Invoice original = invoice.getOriginalInvoice();
            return "Bonjour,\n\n"
                + "Veuillez trouver ci-joint l'avoir " + invoice.getInvoiceNumber() + " du " + invoice.getIssueDate().format(DATE)
                + ", d'un montant de " + AmountUtils.format(invoice.getTotalAmount()) + ", sur la facture "
                + original.getInvoiceNumber() + " du " + original.getIssueDate().format(DATE) + ".\n"
                + "\nCordialement,\n" + invoice.getIssuerFullName();
        }
        StringBuilder text = new StringBuilder("Bonjour,\n\n")
            .append("Veuillez trouver ci-joint la facture ").append(invoice.getInvoiceNumber())
            .append(" du ").append(invoice.getIssueDate().format(DATE))
            .append(", d'un montant de ").append(AmountUtils.format(invoice.getAmountDue()));
        if (invoice.getStampDutyAmount().signum() > 0) {
            text.append(" (droit de timbre inclus)");
        }
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            text.append(".\nCette facture est réglée : merci pour votre paiement.\n");
        } else if (invoice.getDueDate() != null) {
            text.append(", payable avant le ").append(invoice.getDueDate().format(DATE)).append(".\n");
        } else {
            text.append(".\n");
        }
        if (invoice.getStatus() != InvoiceStatus.PAID && invoice.getIssuerBankAccount() != null
                && !invoice.getIssuerBankAccount().isBlank()) {
            text.append("Règlement : ").append(invoice.getIssuerBankAccount()).append("\n");
        }
        text.append("\nCordialement,\n").append(invoice.getIssuerFullName());
        if (invoice.getIssuerPhone() != null && !invoice.getIssuerPhone().isBlank()) {
            text.append("\n").append(invoice.getIssuerPhone());
        }
        return text.toString();
    }
}
