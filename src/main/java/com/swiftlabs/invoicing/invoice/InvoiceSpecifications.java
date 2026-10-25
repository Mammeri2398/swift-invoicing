package com.swiftlabs.invoicing.invoice;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

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

    /** The user's text is matched literally: % and _ are not wildcards. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
