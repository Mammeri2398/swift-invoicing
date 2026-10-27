package com.swiftlabs.invoicing.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/** A company of the installation, with its own schema (see {@link CompanySchemas}). */
@Entity
@Table(name = "company", schema = CompanySchemas.SHARED)
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** As shown in the company switcher; the company's own details are in its issuer row. */
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "schema_name", nullable = false, length = 63, unique = true)
    private String schemaName;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Company() {
    }

    /** Its schema is named after its id, known once saved: a unique placeholder until then. */
    public Company(String name) {
        this.name = name;
        this.schemaName = "pending_" + UUID.randomUUID().toString().replace("-", "");
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSchemaName() { return schemaName; }
    void setSchemaName(String schemaName) { this.schemaName = schemaName; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
