package io.muzoo.ssc.controlmap.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A security finding (vulnerability / risk). Carries a human-facing {@code reference}
 * ({@code CM-YYYY-NNNN}, distinct from the DB id), severity, optional CVSS, workflow status, owning
 * analyst, and the embedded {@link Asset}. {@code createdAt}/{@code updatedAt} are stamped by JPA
 * lifecycle callbacks. Status advances only through role-gated, logged transitions (chunk #15).
 */
@Entity
@Table(name = "finding")
public class Finding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String reference;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    @Column(name = "cvss_score", precision = 3, scale = 1)
    private BigDecimal cvssScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FindingStatus status = FindingStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Embedded
    private Asset asset;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Soft-delete marker: null = active; non-null = deleted at this instant (the row is retained). */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Finding() {
        // JPA
    }

    public Finding(String reference, String title, String description, Severity severity,
                   BigDecimal cvssScore, User owner, Asset asset) {
        this.reference = reference;
        this.title = title;
        this.description = description;
        this.severity = severity;
        this.cvssScore = cvssScore;
        this.owner = owner;
        this.asset = asset;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public BigDecimal getCvssScore() {
        return cvssScore;
    }

    public void setCvssScore(BigDecimal cvssScore) {
        this.cvssScore = cvssScore;
    }

    public FindingStatus getStatus() {
        return status;
    }

    public void setStatus(FindingStatus status) {
        this.status = status;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public Asset getAsset() {
        return asset;
    }

    public void setAsset(Asset asset) {
        this.asset = asset;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Marks this finding deleted (soft delete) — the row and its audit trail are retained. */
    public void markDeleted() {
        this.deletedAt = Instant.now();
    }
}
