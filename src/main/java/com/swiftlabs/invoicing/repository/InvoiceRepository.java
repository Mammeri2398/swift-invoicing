package com.swiftlabs.invoicing.repository;

import com.swiftlabs.invoicing.domain.Invoice;
import com.swiftlabs.invoicing.domain.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    boolean existsByInvoiceNumber(String invoiceNumber);

    java.util.Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    List<Invoice> findByClientId(Long clientId);

    @Query("SELECT i FROM Invoice i WHERE i.client.id = :clientId AND i.status <> 'CANCELLED'")
    List<Invoice> findByClientNotCancelled(@Param("clientId") Long clientId);

    @Query("SELECT COALESCE(SUM(i.totalAmount), 0) FROM Invoice i WHERE i.invoiceYear = :year AND i.status IN ('ISSUED', 'PAID')")
    BigDecimal sumIssuedPaidByYear(@Param("year") Integer year);

    List<Invoice> findByStatus(InvoiceStatus status);

    List<Invoice> findByInvoiceYear(Integer year);

    boolean existsByInvoiceYearAndSequenceNumber(Integer year, Integer sequenceNumber);
}
