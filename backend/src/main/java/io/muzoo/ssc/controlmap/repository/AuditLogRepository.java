package io.muzoo.ssc.controlmap.repository;

import io.muzoo.ssc.controlmap.domain.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link AuditLog} — the activity trail in Finding Detail, oldest first (#16). */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByFinding_IdOrderByTimestampAscIdAsc(Long findingId);
}
