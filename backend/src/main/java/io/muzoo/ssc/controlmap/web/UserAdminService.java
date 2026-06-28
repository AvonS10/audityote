package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.domain.Role;
import io.muzoo.ssc.controlmap.domain.User;
import io.muzoo.ssc.controlmap.domain.UserAuditLog;
import io.muzoo.ssc.controlmap.repository.UserAuditLogRepository;
import io.muzoo.ssc.controlmap.repository.UserRepository;
import io.muzoo.ssc.controlmap.web.dto.UserSummary;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin user-management (#admin), invoked only by ADMINs (gated in {@link UserAdminController}). Each
 * mutation writes a {@link UserAuditLog} entry. Two self-lockout guards: an admin cannot change their
 * own role or deactivate themselves, so the system can never be left without a way back in.
 */
@Service
@Transactional
public class UserAdminService {

    private final UserRepository users;
    private final UserAuditLogRepository userAudit;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(UserRepository users, UserAuditLogRepository userAudit, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.userAudit = userAudit;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserSummary> list() {
        return users.findAll(Sort.by(Sort.Direction.ASC, "name")).stream().map(UserSummary::from).toList();
    }

    public UserSummary changeRole(String actorEmail, Long targetId, String roleWire) {
        User actor = currentUser(actorEmail);
        User target = requireUser(targetId);
        Role newRole = parseRole(roleWire);
        if (target.getId().equals(actor.getId())) {
            throw new ForbiddenException("You cannot change your own role.");
        }
        Role oldRole = target.getRole();
        if (oldRole != newRole) {
            target.setRole(newRole);
            audit(actor, target, "role-changed", oldRole + " → " + newRole);
        }
        return UserSummary.from(target);
    }

    public UserSummary setActive(String actorEmail, Long targetId, boolean active) {
        User actor = currentUser(actorEmail);
        User target = requireUser(targetId);
        if (target.getId().equals(actor.getId())) {
            throw new ForbiddenException("You cannot deactivate your own account.");
        }
        if (target.isActive() != active) {
            target.setActive(active);
            audit(actor, target, active ? "reactivated" : "deactivated", null);
        }
        return UserSummary.from(target);
    }

    public void resetPassword(String actorEmail, Long targetId, String newPassword) {
        User actor = currentUser(actorEmail);
        User target = requireUser(targetId);
        target.setPasswordHash(passwordEncoder.encode(newPassword));
        audit(actor, target, "password-reset", null);
    }

    private void audit(User actor, User target, String action, String detail) {
        userAudit.save(new UserAuditLog(actor, target, action, detail));
    }

    private Role parseRole(String wire) {
        try {
            return Role.valueOf(wire.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid role: " + wire);
        }
    }

    private User requireUser(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User not found: " + id));
    }

    private User currentUser(String email) {
        return users.findByEmail(email).orElseThrow(() -> new ForbiddenException("Authenticated user not found."));
    }
}
