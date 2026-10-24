package com.swiftlabs.invoicing.invoice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceEventRepository extends JpaRepository<InvoiceEvent, Long> {

    List<InvoiceEvent> findByInvoiceIdOrderByOccurredAtAscIdAsc(Long invoiceId);

    void deleteByInvoice(Invoice invoice);
}
