package io.muzoo.ssc.controlmap.repository;

import io.muzoo.ssc.controlmap.domain.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for {@link AuditLog} — the activity trail in Finding Detail, oldest first (#16). */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByFinding_IdOrderByTimestampAscIdAsc(Long findingId);

    /**
     * The 'return' audit events for findings the given user owns that are currently RETURNED — newest
     * first, finding + actor fetched. Backs the analyst return-notifications (#4); a finding bounced
     * more than once yields multiple rows, so the caller keeps the newest per finding.
     */
    @Query("""
            select a from AuditLog a
            join fetch a.finding f
            join fetch a.actor
            where a.action = 'return'
              and f.owner.email = :email
              and f.status = io.muzoo.ssc.controlmap.domain.FindingStatus.RETURNED
              and f.deletedAt is null
            order by a.timestamp desc, a.id desc
            """)
    List<AuditLog> findReturnNotifications(@Param("email") String email);

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
