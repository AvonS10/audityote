package io.muzoo.ssc.controlmap.repository;

import io.muzoo.ssc.controlmap.domain.UserAuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Data access for the admin user-management audit trail (#admin). */
public interface UserAuditLogRepository extends JpaRepository<UserAuditLog, Long> {

    /**
     * Every admin action chronologically, with actor and target fetched — backs the ADMIN-only
     * user-audit export (same system-of-record ordering as the finding audit export).
     */
    @Query("""
            select a from UserAuditLog a
            join fetch a.actor
            join fetch a.targetUser
            order by a.timestamp asc, a.id asc
            """)
    List<UserAuditLog> findAllForExport();
}
