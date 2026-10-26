package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientService;
import com.swiftlabs.invoicing.common.Messages;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Client statement (relevé de compte): what the client was invoiced and what they paid, over a
 * period, with the balance carried over from before it. Invoices are debits (net to pay); credit
 * notes and payments are credits. Cancelled invoices, drafts, quotes and pro formas do not appear.
 * Line labels are in the current request's language (the statement is a page for the user).
 */
@Service
@Transactional(readOnly = true)
public class StatementService {

    /** One line: {@code balance} is the running balance after it. */
    public record Entry(LocalDate date, String label, Invoice document, BigDecimal debit, BigDecimal credit, BigDecimal balance) {
    }

    public record Statement(Client client, LocalDate from, LocalDate to, BigDecimal openingBalance, List<Entry> entries,
                            BigDecimal totalDebit, BigDecimal totalCredit, BigDecimal closingBalance) {
    }

    private record Movement(LocalDate date, int order, String label, Invoice document, BigDecimal debit, BigDecimal credit) {
    }

    private final InvoiceRepository invoiceRepository;
    private final InvoicePaymentRepository paymentRepository;
    private final ClientService clientService;
    private final Messages messages;

    public StatementService(InvoiceRepository invoiceRepository, InvoicePaymentRepository paymentRepository,
                            ClientService clientService, Messages messages) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.clientService = clientService;
        this.messages = messages;
    }

    public Statement statement(Long clientId, LocalDate from, LocalDate to) {
        Client client = clientService.findById(clientId);
        List<Movement> movements = new ArrayList<>();
        for (Invoice document : invoiceRepository.findByClientIdAndKindInAndStatusInOrderByIssueDateAscIdAsc(
                clientId, DocumentKind.ACCOUNTING, List.of(InvoiceStatus.ISSUED, InvoiceStatus.PAID))) {
            if (document.isCreditNote()) {
                movements.add(new Movement(document.getIssueDate(), 1,
                    messages.get("clientStatement.entry.creditNote", document.getOriginalInvoice().getInvoiceNumber()),
                    document, BigDecimal.ZERO, document.getTotalAmount()));
            } else {
                movements.add(new Movement(document.getIssueDate(), 0, messages.get("clientStatement.entry.invoice"), document, document.getAmountDue(), BigDecimal.ZERO));
            }
        }
        for (InvoicePayment payment : paymentRepository.findByClient(clientId)) {
            if (payment.getInvoice().getStatus() == InvoiceStatus.CANCELLED) {
                continue;
            }
            String label = payment.getMethod() != null
                ? messages.get("clientStatement.entry.paymentBy", messages.get("clientStatement.method." + payment.getMethod().name()))
                : messages.get("clientStatement.entry.payment");
            if (payment.getReference() != null) {
                label = messages.get("clientStatement.entry.withReference", label, payment.getReference());
            }
            movements.add(new Movement(payment.getPaidOn(), 2, label, payment.getInvoice(), BigDecimal.ZERO, payment.getAmount()));
        }
        movements.sort(Comparator.comparing(Movement::date).thenComparingInt(Movement::order));

        BigDecimal opening = BigDecimal.ZERO;
        BigDecimal balance = BigDecimal.ZERO;
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        List<Entry> entries = new ArrayList<>();
        for (Movement movement : movements) {
            if (movement.date().isAfter(to)) {
                break;
            }
            balance = balance.add(movement.debit()).subtract(movement.credit());
            if (movement.date().isBefore(from)) {
                opening = balance;
                continue;
            }
            debit = debit.add(movement.debit());
            credit = credit.add(movement.credit());
            entries.add(new Entry(movement.date(), movement.label(), movement.document(), movement.debit(), movement.credit(), balance));
        }
        return new Statement(client, from, to, opening, entries, debit, credit, balance);
    }
}
