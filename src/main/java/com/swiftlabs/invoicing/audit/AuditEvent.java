package com.swiftlabs.invoicing.audit;

import com.swiftlabs.invoicing.company.CompanySchemas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * An entry of the activity journal: who did what, when and from where. Written by {@link AuditRecorder}, never changed;
 * kept in the shared schema with the company it happened in (none before one is chosen: sign-in, sign-out).
 */
@Entity
@Immutable
@Table(name = "audit_event", schema = CompanySchemas.SHARED)
public class AuditEvent {

    @Id
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private AuditKind kind;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "method", length = 10)
    private String method;

    /** The address, with the filters of a GET page; secrets (reset links, codes) masked. */
    @Column(name = "path", length = 1000)
    private String path;

    @Column(name = "status")
    private Integer status;

    @Column(name = "duration_ms")
    private Integer durationMs;

    /** The code that answered: "ClientController#delete". */
    @Column(name = "handler", length = 150)
    private String handler;

    @Column(name = "ip", length = 64)
    private String ip;

    /** See {@link AuditSubjects.Subject}. */
    @Column(name = "subject_type", length = 30)
    private String subjectType;

    @Column(name = "subject")
    private String subject;

    protected AuditEvent() {
    }

    public Long getId() { return id; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public AuditKind getKind() { return kind; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public Long getCompanyId() { return companyId; }
    public String getCompanyName() { return companyName; }
    public String getMethod() { return method; }
    public String getPath() { return path; }
    public Integer getStatus() { return status; }
    public Integer getDurationMs() { return durationMs; }
    public String getHandler() { return handler; }
    public String getIp() { return ip; }
    public String getSubjectType() { return subjectType; }
    public String getSubject() { return subject; }

    /** Refused (403) or failed (5xx, 4xx): shown in red. */
    public boolean isFailure() {
        return kind == AuditKind.SIGN_IN_FAILED || kind == AuditKind.SIGN_IN_LOCKED || (status != null && status >= 400);
    }
}
