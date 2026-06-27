package io.muzoo.ssc.controlmap.audit;

import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.domain.FindingStatus;
import io.muzoo.ssc.controlmap.domain.User;

/**
 * Domain event: an audit-worthy thing happened to a {@link Finding} (Observer pattern, PLAN §8/§11).
 * The service publishes it; {@link AuditTrailListener} observes and records an immutable audit row —
 * so the workflow service never depends on the audit log. {@code fromStatus}/{@code toStatus} are set
 * for transitions (and creation, where {@code fromStatus} is null); {@code comment} is optional.
 */
public record FindingAuditEvent(
        Finding finding,
        User actor,
        String action,
        FindingStatus fromStatus,
        FindingStatus toStatus,
        String comment) {
}
