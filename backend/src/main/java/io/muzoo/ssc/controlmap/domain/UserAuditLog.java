package io.muzoo.ssc.controlmap.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Immutable audit-trail entry for an admin user-management action (#admin): which admin did what to
 * which user, and when. Kept separate from the finding-scoped {@link AuditLog}; the target user is
 * retained even after deactivation, so the record never dangles.
 */
@Entity
@Table(name = "user_audit_log")
public class UserAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(columnDefinition = "text")
    private String detail;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant timestamp;

    protected UserAuditLog() {
        // JPA
    }

    public UserAuditLog(User actor, User targetUser, String action, String detail) {
        this.actor = actor;
        this.targetUser = targetUser;
        this.action = action;
        this.detail = detail;
        this.timestamp = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getActor() {
        return actor;
    }

    public User getTargetUser() {
        return targetUser;
    }

    public String getAction() {
        return action;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
