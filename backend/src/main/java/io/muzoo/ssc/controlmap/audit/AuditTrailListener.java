package io.muzoo.ssc.controlmap.audit;

import io.muzoo.ssc.controlmap.domain.AuditLog;
import io.muzoo.ssc.controlmap.repository.AuditLogRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Observer that records every {@link FindingAuditEvent} as an immutable {@link AuditLog} row (PLAN
 * §8/§11). Runs synchronously inside the publishing transaction, so the audit entry commits
 * atomically with the change that caused it — and rolls back with it if that change fails. This keeps
 * auditing decoupled from the workflow service: the service publishes, the trail records itself.
 */
@Component
public class AuditTrailListener {

    private final AuditLogRepository auditLog;

    public AuditTrailListener(AuditLogRepository auditLog) {
        this.auditLog = auditLog;
    }

    @EventListener
    public void on(FindingAuditEvent event) {
        auditLog.save(new AuditLog(
                event.finding(), event.actor(), event.action(),
                event.fromStatus(), event.toStatus(), event.comment()));
    }
}
