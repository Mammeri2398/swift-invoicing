package com.swiftlabs.invoicing.audit;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Writes the entries of the activity journal. Each in its own transaction, so that it is kept when the work it
 * records fails; and a journal that cannot be written never breaks the page or the sign-in (only logged).
 */
@Component
public class AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);
    private static final String INSERT = "INSERT INTO public.audit_event (occurred_at, kind, user_id, username, company_id,"
        + " company_name, method, path, status, duration_ms, handler, ip, subject_type, subject)"
        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate ownTransaction;
    private final Clock clock;

    public AuditRecorder(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /** Who did it: the signed-in user and their company, or a name typed at sign-in (null when anonymous). */
    public record Actor(Long userId, String username, Long companyId, String companyName) {

        public static final Actor ANONYMOUS = new Actor(null, null, null, null);

        public static Actor of(Authentication authentication) {
            if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
                return ANONYMOUS;
            }
            if (authentication.getPrincipal() instanceof AppUserPrincipal principal) {
                return of(principal);
            }
            return new Actor(null, authentication.getName(), null, null);
        }

        public static Actor of(AppUserPrincipal principal) {
            return new Actor(principal.getId(), principal.getUsername(), principal.getCompanyId(), principal.getCompanyName());
        }

        public boolean isAnonymous() {
            return username == null;
        }
    }

    /** A sign-in, sign-out or end of session. */
    public void record(AuditKind kind, Actor actor, String ip) {
        record(kind, actor, null, null, null, null, null, ip, null);
    }

    /** A page or an action, and what it is about (null: nothing in particular). */
    public void record(AuditKind kind, Actor actor, String method, String path, Integer status, Integer durationMs, String handler,
                       String ip, AuditSubjects.Subject subject) {
        try {
            ownTransaction.executeWithoutResult(transaction -> jdbcTemplate.update(INSERT,
                Timestamp.valueOf(LocalDateTime.now(clock)), kind.name(), actor.userId(), truncate(actor.username(), 100),
                actor.companyId(), truncate(actor.companyName(), 255), method, truncate(path, 1000), status, durationMs,
                truncate(handler, 150), truncate(ip, 64), subject == null ? null : subject.type(),
                subject == null ? null : truncate(subject.label(), 255)));
        } catch (RuntimeException ex) {
            log.warn("Activity journal not written ({} by {}): {}", kind, actor.username(), ex.toString());
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
