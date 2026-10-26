package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.common.AmountUtils;
import com.swiftlabs.invoicing.common.Language;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Audit trail of the invoices. Events are written in the transaction of the action itself:
 * a failed action leaves no event, and a recorded action always has its event.
 *
 * The details of an event are stored in French (the history is a record, never rewritten); the
 * details written by the application are shown in the reader's language (see {@link #detail}).
 */
@Component
public class InvoiceAudit {

    private static final Logger log = LoggerFactory.getLogger(InvoiceAudit.class);

    /** Stored details: written by the methods below, read back by {@link #detail}. */
    static final String DRAFT_INVOICE_CREATED = "facture brouillon créée";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern ON_INVOICE = Pattern.compile("sur la facture (\\S+)");
    private static final Pattern FROM_DOCUMENT = Pattern.compile("depuis le (devis|facture pro forma) (\\S+) du (\\S+)");
    private static final Pattern PAYMENT = Pattern.compile("(.+?) le (\\d{2}/\\d{2}/\\d{4})(?: \\((.*)\\))?");

    private final InvoiceEventRepository eventRepository;
    private final Clock clock;
    private final Messages messages;

    public InvoiceAudit(InvoiceEventRepository eventRepository, Clock clock, Messages messages) {
        this.eventRepository = eventRepository;
        this.clock = clock;
        this.messages = messages;
    }

    /** Records that the current user performed {@code action} on the invoice. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Invoice invoice, InvoiceEvent.Action action) {
        record(invoice, action, null);
    }

    /** Same, with a precision shown in the history (e.g. the recipients of an email). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Invoice invoice, InvoiceEvent.Action action, String detail) {
        String username = currentUsername();
        eventRepository.save(new InvoiceEvent(invoice, action, username, LocalDateTime.now(clock), detail));
        log.info("Invoice {} ({}): {} by {}{}", invoice.getId(), invoice.getInvoiceNumber(), action, username,
            detail != null ? " (" + detail + ")" : "");
    }

    /** Forgets the history of a draft that is being deleted (a draft has no legal existence). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void forget(Invoice invoice) {
        eventRepository.deleteByInvoice(invoice);
    }

    @Transactional(readOnly = true)
    public List<InvoiceEvent> history(Long invoiceId) {
        return eventRepository.findByInvoiceIdOrderByOccurredAtAscIdAsc(invoiceId);
    }

    /** Detail of an event about a credit note and its invoice: "sur la facture FA-2026-0001". */
    static String onInvoice(Invoice invoice) {
        return "sur la facture " + invoice.getInvoiceNumber();
    }

    /** Detail of an invoice created from a quote or a pro forma: "depuis le devis DV-2026-0001 du 01/03/2026". */
    static String fromDocument(Invoice document) {
        return "depuis le " + (document.isQuote() ? "devis " : "facture pro forma ") + document.getInvoiceNumber()
            + " du " + document.getIssueDate().format(DATE);
    }

    /** Detail of a payment: "400,00 DA le 01/02/2026 (chèque, CHQ 0042)". */
    static String payment(InvoicePayment payment) {
        StringBuilder text = new StringBuilder(AmountUtils.format(payment.getAmount()))
            .append(" le ").append(payment.getPaidOn().format(DATE));
        if (payment.getMethod() != null) {
            text.append(" (").append(PaymentService.methodLabel(payment.getMethod()));
            if (payment.getReference() != null) {
                text.append(", ").append(payment.getReference());
            }
            text.append(")");
        } else if (payment.getReference() != null) {
            text.append(" (").append(payment.getReference()).append(")");
        }
        return text.toString();
    }

    /**
     * The detail of an event as shown on the invoice page, in the reader's language: the details
     * written by the application (see above) are translated, anything else (email addresses,
     * numbers, older free text) is shown as stored.
     */
    public String detail(InvoiceEvent event) {
        String detail = event.getDetail();
        if (detail == null) {
            return null;
        }
        if (event.getAction() == InvoiceEvent.Action.SENT || event.getAction() == InvoiceEvent.Action.REMINDED) {
            return messages.get(MessageProperties.INVOICE_HISTORY_SENT_TO, detail);
        }
        if (messages.current() == Language.FR) {
            return detail;
        }
        Matcher matcher = ON_INVOICE.matcher(detail);
        if (matcher.matches()) {
            return messages.get(MessageProperties.INVOICE_HISTORY_ON_INVOICE, matcher.group(1));
        }
        matcher = FROM_DOCUMENT.matcher(detail);
        if (matcher.matches()) {
            String key = matcher.group(1).equals("devis")
                ? MessageProperties.INVOICE_HISTORY_FROM_QUOTE : MessageProperties.INVOICE_HISTORY_FROM_PROFORMA;
            return messages.get(key, matcher.group(2), matcher.group(3));
        }
        if (detail.equals(DRAFT_INVOICE_CREATED)) {
            return messages.get(MessageProperties.INVOICE_HISTORY_DRAFT_INVOICE_CREATED);
        }
        matcher = PAYMENT.matcher(detail);
        if ((event.getAction() == InvoiceEvent.Action.PAYMENT || event.getAction() == InvoiceEvent.Action.PAYMENT_DELETED)
                && matcher.matches()) {
            return matcher.group(3) == null
                ? messages.get(MessageProperties.INVOICE_HISTORY_PAYMENT, matcher.group(1), matcher.group(2))
                : messages.get(MessageProperties.INVOICE_HISTORY_PAYMENT_WITH, matcher.group(1), matcher.group(2),
                    paymentPrecision(matcher.group(3)));
        }
        return detail;
    }

    /** "chèque, CHQ 0042" in the reader's language: the payment method translated, the reference as typed. */
    private String paymentPrecision(String stored) {
        for (PaymentMethod method : PaymentMethod.values()) {
            String label = PaymentService.methodLabel(method);
            if (stored.equals(label)) {
                return messages.label(method);
            }
            if (stored.startsWith(label + ", ")) {
                return messages.label(method) + stored.substring(label.length());
            }
        }
        return stored;
    }

    /** The logged-in user's name; "système" for actions without a user (e.g. batch jobs). */
    private static String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() ? authentication.getName() : "système";
    }
}
