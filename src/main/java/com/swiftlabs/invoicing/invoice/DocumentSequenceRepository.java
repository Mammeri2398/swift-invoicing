package com.swiftlabs.invoicing.invoice;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/**
 * Per-year counters of quotes and pro formas (document_sequence, one row per kind and year).
 * Same allocation as the invoice counter: the row is created if absent, then locked
 * (SELECT ... FOR UPDATE) until the end of the issuing transaction, so numbers are gap-free.
 */
@Repository
public class DocumentSequenceRepository {

    private final EntityManager entityManager;

    public DocumentSequenceRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public int next(DocumentKind kind, int year) {
        entityManager.createNativeQuery("INSERT INTO document_sequence (kind, doc_year, last_value) VALUES (?1, ?2, 0) ON CONFLICT DO NOTHING")
            .setParameter(1, kind.name()).setParameter(2, year).executeUpdate();
        Number last = (Number) entityManager.createNativeQuery("SELECT last_value FROM document_sequence WHERE kind = ?1 AND doc_year = ?2 FOR UPDATE")
            .setParameter(1, kind.name()).setParameter(2, year).getSingleResult();
        int next = last.intValue() + 1;
        entityManager.createNativeQuery("UPDATE document_sequence SET last_value = ?1 WHERE kind = ?2 AND doc_year = ?3")
            .setParameter(1, next).setParameter(2, kind.name()).setParameter(3, year).executeUpdate();
        return next;
    }
}
