package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.domain.AuditLog;
import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.repository.AuditLogRepository;
import io.muzoo.ssc.controlmap.web.dto.NotificationResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Return-notifications for the signed-in user (#4): the findings they own that a reviewer has handed
 * back (RETURNED), each carrying the latest return comment. Read-only; derived from the audit trail.
 */
@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final AuditLogRepository auditLog;

    public NotificationService(AuditLogRepository auditLog) {
        this.auditLog = auditLog;
    }

    public List<NotificationResponse> forUser(String email) {
        Set<Long> seen = new HashSet<>();
        List<NotificationResponse> notifications = new ArrayList<>();
        // Rows arrive newest-first; keep only the most recent 'return' per finding.
        for (AuditLog entry : auditLog.findReturnNotifications(email)) {
            Finding finding = entry.getFinding();
            if (!seen.add(finding.getId())) {
                continue;
            }
            notifications.add(new NotificationResponse(
                    finding.getId(),
                    finding.getReference(),
                    finding.getTitle(),
                    FindingMapper.severityToWire(finding.getSeverity()),
                    entry.getActor().getName(),
                    entry.getTimestamp(),
                    entry.getComment()));
        }
        return notifications;
    }
}
