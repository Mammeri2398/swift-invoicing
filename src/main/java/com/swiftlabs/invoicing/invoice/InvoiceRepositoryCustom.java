package com.swiftlabs.invoicing.invoice;

import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

/** Queries Spring Data cannot derive (implemented in InvoiceRepositoryCustomImpl). */
interface InvoiceRepositoryCustom {

    /** Sum of the totals of every invoice matching the specification (not just one page). */
    BigDecimal sumTotal(Specification<Invoice> specification);

    /**
     * Locks the invoice's row until the end of the transaction and reloads it: a change of state (issue, payment,
     * cancellation...) made at the same time waits, then sees this one's result instead of overwriting it.
     */
    void lockForUpdate(Invoice invoice);

    /** A new version of the invoice at commit, even when only its lines changed (they are rows of their own). */
    void markChanged(Invoice invoice);
}
