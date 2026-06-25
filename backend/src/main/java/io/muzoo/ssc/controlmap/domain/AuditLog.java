package io.muzoo.ssc.controlmap.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Immutable audit-trail entry for a {@link Finding} — who did what, and (for workflow transitions)
 * the status change and optional comment. Written by the Observer on every transition (chunk #16,
 * PLAN §8). The Java field is {@code timestamp}; the column is {@code occurred_at} to avoid the
 * reserved word {@code timestamp} in PostgreSQL.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "finding_id", nullable = false)
    private Finding finding;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Column(nullable = false, length = 50)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private FindingStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 20)
    private FindingStatus toStatus;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant timestamp;

    protected AuditLog() {
        // JPA
    }

    public AuditLog(Finding finding, User actor, String action,
                    FindingStatus fromStatus, FindingStatus toStatus, String comment) {
        this.finding = finding;
        this.actor = actor;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.comment = comment;
        this.timestamp = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Finding getFinding() {
        return finding;
    }

    public User getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public FindingStatus getFromStatus() {
        return fromStatus;
    }

    public FindingStatus getToStatus() {
        return toStatus;
    }

    public String getComment() {
        return comment;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
