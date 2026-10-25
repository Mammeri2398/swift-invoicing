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

    /** Every matching invoice (exports), with its client loaded in the same query. */
    @Override
    @EntityGraph(attributePaths = "client")
    List<Invoice> findAll(Specification<Invoice> specification, Sort sort);

    /** An invoice ready to display or print: client and lines loaded. */
    @EntityGraph(attributePaths = {"client", "lines"})
    Optional<Invoice> findWithLinesById(Long id);

    boolean existsByInvoiceNumber(String invoiceNumber);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    long countByClientId(Long clientId);

    long countByStatus(InvoiceStatus status);

    @EntityGraph(attributePaths = "client")
    List<Invoice> findTop10ByOrderByCreatedAtDescIdDesc();

    /** Turnover: totals before TVA (HT). */
    @Query("SELECT COALESCE(SUM(i.subtotalAmount), 0) FROM Invoice i WHERE i.invoiceYear = :year AND i.status IN ('ISSUED', 'PAID')")
    BigDecimal sumIssuedPaidByYear(@Param("year") Integer year);

    /** Issued, paid and cancelled invoices of the year, by month of issue and status. */
    @Query("SELECT new com.swiftlabs.invoicing.invoice.MonthlyTotal(MONTH(i.issueDate), i.status, COUNT(i), SUM(i.subtotalAmount), SUM(i.vatAmount))"
        + " FROM Invoice i WHERE i.invoiceYear = :year AND i.status <> 'DRAFT' GROUP BY MONTH(i.issueDate), i.status")
    List<MonthlyTotal> monthlyTotals(@Param("year") Integer year);

    /** The years that have issued invoices, most recent first. */
    @Query("SELECT DISTINCT i.invoiceYear FROM Invoice i WHERE i.invoiceYear IS NOT NULL ORDER BY i.invoiceYear DESC")
    List<Integer> findInvoiceYears();

    @Query("SELECT COALESCE(SUM(i.totalAmount), 0) FROM Invoice i WHERE i.status = :status")
    BigDecimal sumByStatus(@Param("status") InvoiceStatus status);
}
