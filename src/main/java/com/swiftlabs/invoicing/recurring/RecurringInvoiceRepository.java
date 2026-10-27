package com.swiftlabs.invoicing.recurring;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RecurringInvoiceRepository extends JpaRepository<RecurringInvoice, Long> {

    /** Every model with its client, by name, for the list. */
    @Query("SELECT r FROM RecurringInvoice r JOIN FETCH r.client ORDER BY LOWER(r.name), r.id")
    List<RecurringInvoice> findAllWithClient();

    /** The active models: few, so the dates are checked in Java. */
    List<RecurringInvoice> findByActiveTrueOrderById();
}
