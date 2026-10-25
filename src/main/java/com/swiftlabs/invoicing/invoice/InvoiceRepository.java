package com.swiftlabs.invoicing.invoice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice>,
    InvoiceRepositoryCustom {

    /** One page of the list, with each invoice's client loaded in the same query (no N+1). */
    @Override
    @EntityGraph(attributePaths = "client")
    Page<Invoice> findAll(Specification<Invoice> specification, Pageable pageable);

    /** Every matching document (exports), with its client and original invoice loaded in the same query. */
    @Override
    @EntityGraph(attributePaths = {"client", "originalInvoice"})
    List<Invoice> findAll(Specification<Invoice> specification, Sort sort);

    /** An invoice ready to display or print: client and lines loaded. */
    @EntityGraph(attributePaths = {"client", "lines"})
    Optional<Invoice> findWithLinesById(Long id);

    boolean existsByInvoiceNumber(String invoiceNumber);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    long countByClientId(Long clientId);

    long countByStatus(InvoiceStatus status);

    /** Credit notes of an invoice, oldest first. */
    List<Invoice> findByOriginalInvoiceIdOrderByIdAsc(Long originalInvoiceId);

    /** TTC already credited on an invoice: its issued credit notes. */
    @Query("SELECT COALESCE(SUM(c.totalAmount), 0) FROM Invoice c WHERE c.originalInvoice.id = :id AND c.status = 'ISSUED'")
    BigDecimal sumIssuedCreditNotes(@Param("id") Long originalInvoiceId);

    /** Issued invoices still owed in part: not fully cancelled by credit notes. */
    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.kind = 'INVOICE' AND i.status = 'ISSUED' AND i.totalAmount >"
        + " (SELECT COALESCE(SUM(c.totalAmount), 0) FROM Invoice c WHERE c.originalInvoice = i AND c.status = 'ISSUED')")
    long countUnpaid();

    /** What clients pay on issued invoices: TTC plus droit de timbre. */
    @Query("SELECT COALESCE(SUM(i.totalAmount + i.stampDutyAmount), 0) FROM Invoice i WHERE i.kind = 'INVOICE' AND i.status = 'ISSUED'")
    BigDecimal sumUnpaidInvoices();

    /** Credit notes on invoices not paid yet: they reduce what clients still owe. */
    @Query("SELECT COALESCE(SUM(c.totalAmount), 0) FROM Invoice c WHERE c.kind = 'CREDIT_NOTE' AND c.status = 'ISSUED'"
        + " AND c.originalInvoice.status = 'ISSUED'")
    BigDecimal sumCreditNotesOnUnpaidInvoices();

    @EntityGraph(attributePaths = "client")
    List<Invoice> findTop10ByOrderByCreatedAtDescIdDesc();

    /** Turnover: totals before TVA (HT), issued credit notes subtracted. */
    @Query("SELECT COALESCE(SUM(CASE WHEN i.kind = 'CREDIT_NOTE' THEN -i.subtotalAmount ELSE i.subtotalAmount END), 0)"
        + " FROM Invoice i WHERE i.invoiceYear = :year AND i.status IN ('ISSUED', 'PAID')")
    BigDecimal sumIssuedPaidByYear(@Param("year") Integer year);

    /** Issued, paid and cancelled invoices of the year, by month of issue and status. */
    @Query("SELECT new com.swiftlabs.invoicing.invoice.MonthlyTotal(MONTH(i.issueDate), i.kind, i.status, COUNT(i),"
        + " SUM(i.subtotalAmount), SUM(i.vatAmount))"
        + " FROM Invoice i WHERE i.invoiceYear = :year AND i.status <> 'DRAFT' GROUP BY MONTH(i.issueDate), i.kind, i.status")
    List<MonthlyTotal> monthlyTotals(@Param("year") Integer year);

    /** The years that have issued invoices, most recent first. */
    @Query("SELECT DISTINCT i.invoiceYear FROM Invoice i WHERE i.invoiceYear IS NOT NULL ORDER BY i.invoiceYear DESC")
    List<Integer> findInvoiceYears();

}
