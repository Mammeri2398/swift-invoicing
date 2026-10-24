package com.swiftlabs.invoicing.invoice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Audit trail of the invoices. Events are written in the transaction of the action itself:
 * a failed action leaves no event, and a recorded action always has its event.
 */
@Component
public class InvoiceAudit {

    private static final Logger log = LoggerFactory.getLogger(InvoiceAudit.class);

    private final InvoiceEventRepository eventRepository;
    private final Clock clock;

    public InvoiceAudit(InvoiceEventRepository eventRepository, Clock clock) {
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    /** Records that the current user performed {@code action} on the invoice. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Invoice invoice, InvoiceEvent.Action action) {
        String username = currentUsername();
        eventRepository.save(new InvoiceEvent(invoice, action, username, LocalDateTime.now(clock)));
        log.info("Invoice {} ({}): {} by {}", invoice.getId(), invoice.getInvoiceNumber(), action, username);
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

    /** The logged-in user's name; "système" for actions without a user (e.g. batch jobs). */
    private static String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() ? authentication.getName() : "système";
    }
}
