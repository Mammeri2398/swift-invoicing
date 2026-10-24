package com.swiftlabs.invoicing.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    boolean existsByInvoiceNumber(String invoiceNumber);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    long countByClientId(Long clientId);

    long countByStatus(InvoiceStatus status);

    @Query("SELECT COALESCE(SUM(i.totalAmount), 0) FROM Invoice i WHERE i.status = :status")
    BigDecimal sumByStatus(@Param("status") InvoiceStatus status);

    List<Invoice> findTop10ByOrderByCreatedAtDescIdDesc();

    @Query("SELECT COALESCE(SUM(i.totalAmount), 0) FROM Invoice i WHERE i.invoiceYear = :year AND i.status IN ('ISSUED', 'PAID')")
    BigDecimal sumIssuedPaidByYear(@Param("year") Integer year);
}
