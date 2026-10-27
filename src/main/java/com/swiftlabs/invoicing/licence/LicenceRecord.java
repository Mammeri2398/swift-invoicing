package com.swiftlabs.invoicing.licence;

import com.swiftlabs.invoicing.company.CompanySchemas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** The licence of this install (single row): the key as pasted, and the trial start. */
@Entity
@Table(name = "licence", schema = CompanySchemas.SHARED)
public class LicenceRecord {

    static final long ID = 1L;

    @Id
    private Long id = ID;

    @Column(name = "licence_key", length = 2000)
    private String licenceKey;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "trial_started_at", nullable = false)
    private LocalDateTime trialStartedAt;

    /** Latest time seen: the clock is never trusted to go backwards. */
    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    protected LicenceRecord() {
    }

    LicenceRecord(LocalDateTime now) {
        this.trialStartedAt = now;
        this.lastSeenAt = now;
    }

    public String getLicenceKey() { return licenceKey; }
    public LocalDateTime getActivatedAt() { return activatedAt; }
    public LocalDateTime getTrialStartedAt() { return trialStartedAt; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }

    void activate(String key, LocalDateTime now) {
        this.licenceKey = key;
        this.activatedAt = now;
    }

    void seen(LocalDateTime now) {
        this.lastSeenAt = now;
    }
}
