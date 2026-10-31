package com.swiftlabs.invoicing.audit;

import com.swiftlabs.invoicing.company.CompanyAccess;
import com.swiftlabs.invoicing.company.CompanyAccessRepository;

import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reading the activity journal (Réglages > Journal d'activité), and forgetting what is too old. */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final char ESCAPE = '\\';
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));

    private final AuditEventRepository repository;
    private final CompanyAccessRepository accessRepository;
    private final JdbcTemplate jdbcTemplate;

    public AuditService(AuditEventRepository repository, CompanyAccessRepository accessRepository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.accessRepository = accessRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Newest first. {@code companyId} not null: what was done in that company, and the sign-ins and sign-outs of its
     * users (done before choosing a company); null: everything.
     */
    @Transactional(readOnly = true)
    public Page<AuditEvent> search(AuditFilter filter, Long companyId, Pageable pageable) {
        List<Long> userIds = companyId == null ? List.of()
            : accessRepository.findByCompanyId(companyId).stream().map(access -> access.getUser().getId()).toList();
        Specification<AuditEvent> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (companyId != null) {
                Predicate here = cb.equal(root.get("companyId"), companyId);
                where.add(userIds.isEmpty() ? here
                    : cb.or(here, cb.and(cb.isNull(root.get("companyId")), root.get("userId").in(userIds))));
            }
            if (filter.getUser() != null && !filter.getUser().isBlank()) {
                where.add(cb.like(cb.lower(root.get("username")), like(filter.getUser()), ESCAPE));
            }
            if (filter.getKind() != null) {
                where.add(cb.equal(root.get("kind"), filter.getKind()));
            }
            if (filter.getFrom() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), filter.getFrom().atStartOfDay()));
            }
            if (filter.getTo() != null) {
                where.add(cb.lessThan(root.get("occurredAt"), filter.getTo().plusDays(1).atStartOfDay()));
            }
            if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                String pattern = like(filter.getSearch());
                where.add(cb.or(cb.like(cb.lower(root.get("path")), pattern, ESCAPE),
                    cb.like(cb.lower(root.get("handler")), pattern, ESCAPE), cb.like(cb.lower(root.get("subject")), pattern, ESCAPE),
                    cb.like(root.get("ip"), pattern, ESCAPE)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST));
    }

    /** Deletes the entries older than {@code before}; the number deleted. */
    @Transactional
    public int deleteOlderThan(LocalDateTime before) {
        int deleted = jdbcTemplate.update("DELETE FROM public.audit_event WHERE occurred_at < ?", Timestamp.valueOf(before));
        if (deleted > 0) {
            log.info("Activity journal: {} entries older than {} deleted", deleted, before.toLocalDate());
        }
        return deleted;
    }

    /** "%text%" for a case-insensitive LIKE, its own % and _ taken literally. */
    private static String like(String text) {
        return "%" + text.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
