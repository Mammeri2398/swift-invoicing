package com.swiftlabs.invoicing.invoice;

import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

/** Queries Spring Data cannot derive (implemented in InvoiceRepositoryCustomImpl). */
interface InvoiceRepositoryCustom {

    /** Sum of the totals of every invoice matching the specification (not just one page). */
    BigDecimal sumTotal(Specification<Invoice> specification);
}
