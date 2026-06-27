package io.muzoo.ssc.controlmap.repository;

import io.muzoo.ssc.controlmap.domain.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Data access for {@link AuditLog} — the activity trail in Finding Detail, oldest first (#16). */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByFinding_IdOrderByTimestampAscIdAsc(Long findingId);

    /**
     * Every audit entry across every finding — including soft-deleted ones — chronologically, with
     * the finding and actor fetched. Backs the full audit-log export (#16d, the system-of-record).
     */
    @Query("""
            select a from AuditLog a
            join fetch a.finding
            join fetch a.actor
            order by a.timestamp asc, a.id asc
            """)
    List<AuditLog> findAllForExport();
}
