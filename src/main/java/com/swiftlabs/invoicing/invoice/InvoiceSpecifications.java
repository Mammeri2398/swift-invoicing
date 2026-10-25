package com.swiftlabs.invoicing.invoice;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Database-side filtering of the invoice list. */
final class InvoiceSpecifications {

    private static final char ESCAPE = '\\';

    private InvoiceSpecifications() {
    }

    static Specification<Invoice> matching(InvoiceFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.getKinds() != null) {
                predicates.add(root.get("kind").in(filter.getKinds()));
            }
            if (filter.isOverdue()) {
                predicates.add(overdue(root, query, cb));
            }
            if (filter.getKind() != null) {
                predicates.add(cb.equal(root.get("kind"), filter.getKind()));
            }
            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }
            if (filter.getYear() != null) {
                // The invoice year is the year of the issue date (drafts have no number yet)
                LocalDate from = LocalDate.of(filter.getYear(), 1, 1);
                predicates.add(cb.between(root.get("issueDate"), from, from.plusYears(1).minusDays(1)));
            }
            if (filter.getClient() != null) {
                predicates.add(cb.equal(root.get("client").get("id"), filter.getClient()));
            }
            if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                String pattern = "%" + escapeLike(filter.getSearch().trim().toLowerCase(Locale.ROOT)) + "%";
                Expression<String> number = cb.lower(root.get("invoiceNumber"));
                Expression<String> snapshotName = cb.lower(root.get("clientName"));
                Expression<String> currentName = cb.lower(root.get("client").get("name"));
                predicates.add(cb.or(
                    cb.like(number, pattern, ESCAPE),
                    cb.like(snapshotName, pattern, ESCAPE),
                    cb.like(currentName, pattern, ESCAPE)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Issued invoice past its due date whose net to pay is above its issued credit notes plus its
     * payments (the balance computed by PaymentService, in the database).
     */
    static Predicate overdue(Root<Invoice> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<BigDecimal> credited = query.subquery(BigDecimal.class);
        Root<Invoice> creditNote = credited.from(Invoice.class);
        credited.select(cb.coalesce(cb.sum(creditNote.<BigDecimal>get("totalAmount")), BigDecimal.ZERO))
            .where(cb.equal(creditNote.get("originalInvoice"), root), cb.equal(creditNote.get("status"), InvoiceStatus.ISSUED));
        Subquery<BigDecimal> paid = query.subquery(BigDecimal.class);
        Root<InvoicePayment> payment = paid.from(InvoicePayment.class);
        paid.select(cb.coalesce(cb.sum(payment.<BigDecimal>get("amount")), BigDecimal.ZERO))
            .where(cb.equal(payment.get("invoice"), root));
        return cb.and(
            cb.equal(root.get("kind"), DocumentKind.INVOICE),
            cb.equal(root.get("status"), InvoiceStatus.ISSUED),
            cb.lessThan(root.get("dueDate"), LocalDate.now()),
            cb.greaterThan(cb.sum(root.<BigDecimal>get("totalAmount"), root.<BigDecimal>get("stampDutyAmount")), cb.sum(credited, paid)));
    }

    /** The user's text is matched literally: % and _ are not wildcards. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
