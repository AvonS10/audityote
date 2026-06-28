package io.muzoo.ssc.controlmap.repository;

import io.muzoo.ssc.controlmap.domain.UserAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for the admin user-management audit trail (#admin). */
public interface UserAuditLogRepository extends JpaRepository<UserAuditLog, Long> {
}
