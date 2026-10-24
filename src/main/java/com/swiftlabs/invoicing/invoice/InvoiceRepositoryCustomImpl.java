package com.swiftlabs.invoicing.invoice;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

class InvoiceRepositoryCustomImpl implements InvoiceRepositoryCustom {

    private final EntityManager entityManager;

    InvoiceRepositoryCustomImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public BigDecimal sumTotal(Specification<Invoice> specification) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<BigDecimal> query = cb.createQuery(BigDecimal.class);
        Root<Invoice> root = query.from(Invoice.class);
        query.select(cb.coalesce(cb.sum(root.<BigDecimal>get("totalAmount")), BigDecimal.ZERO));
        Predicate predicate = specification.toPredicate(root, query, cb);
        if (predicate != null) {
            query.where(predicate);
        }
        return entityManager.createQuery(query).getSingleResult();
    }
}
