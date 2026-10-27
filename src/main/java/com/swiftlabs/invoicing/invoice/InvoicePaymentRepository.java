package com.swiftlabs.invoicing.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, Long> {

    List<InvoicePayment> findByInvoiceIdOrderByPaidOnAscIdAsc(Long invoiceId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM InvoicePayment p WHERE p.invoice.id = :id")
    BigDecimal sumByInvoice(@Param("id") Long invoiceId);

    /** Payments received on invoices not fully paid yet. */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM InvoicePayment p WHERE p.invoice.status = 'ISSUED'")
    BigDecimal sumOnUnpaidInvoices();

    /** Payments per invoice id, for the given invoices. */
    @Query("SELECT p.invoice.id, SUM(p.amount) FROM InvoicePayment p WHERE p.invoice.id IN :ids GROUP BY p.invoice.id")
    List<Object[]> sumByInvoices(@Param("ids") List<Long> invoiceIds);

    /** Payments on issued (not fully paid) invoices, per client id. */
    @Query("SELECT p.invoice.client.id, SUM(p.amount) FROM InvoicePayment p WHERE p.invoice.status = 'ISSUED' GROUP BY p.invoice.client.id")
    List<Object[]> sumOnUnpaidByClient();

    /** Payments of a client's invoices, oldest first (client statement). */
    @Query("SELECT p FROM InvoicePayment p JOIN FETCH p.invoice i WHERE i.client.id = :clientId ORDER BY p.paidOn, p.id")
    List<InvoicePayment> findByClient(@Param("clientId") Long clientId);

    /** Payments received in a period on invoices, oldest first, for the TVA summary on collections. */
    @Query("SELECT p FROM InvoicePayment p JOIN FETCH p.invoice i WHERE i.kind = 'INVOICE' AND p.paidOn BETWEEN :from AND :to"
        + " ORDER BY p.paidOn, p.id")
    List<InvoicePayment> findReceivedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
