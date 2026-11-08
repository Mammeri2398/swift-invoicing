package com.swiftlabs.invoicing.invoice;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
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
        // Credit notes count negatively
        Expression<BigDecimal> signed = cb.<BigDecimal>selectCase()
            .when(cb.equal(root.get("kind"), DocumentKind.CREDIT_NOTE), cb.neg(root.<BigDecimal>get("totalAmount")))
            .otherwise(root.<BigDecimal>get("totalAmount"));
        query.select(cb.coalesce(cb.sum(signed), BigDecimal.ZERO));
        Predicate predicate = specification.toPredicate(root, query, cb);
        if (predicate != null) {
            query.where(predicate);
        }
        return entityManager.createQuery(query).getSingleResult();
    }

    @Override
    public void lockForUpdate(Invoice invoice) {
        // Changes already made in this transaction are written first: the reload must not lose them
        entityManager.flush();
        entityManager.refresh(invoice, LockModeType.PESSIMISTIC_WRITE);
    }

    @Override
    public void markChanged(Invoice invoice) {
        // Pessimistic: the row is already locked for update (lockForUpdate), a weaker lock would be ignored
        entityManager.lock(invoice, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    }
}
