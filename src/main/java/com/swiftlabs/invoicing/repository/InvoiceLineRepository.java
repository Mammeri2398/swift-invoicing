package com.swiftlabs.invoicing.repository;

import com.swiftlabs.invoicing.domain.InvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, Long> {

    void deleteByInvoiceId(Long invoiceId);

    @Query("SELECT l FROM InvoiceLine l WHERE l.invoice.id = :invoiceId ORDER BY l.position ASC")
    List<InvoiceLine> findByInvoiceIdOrderByPositionAsc(@Param("invoiceId") Long invoiceId);
}
