package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.web.dto.ActiveChangeRequest;
import io.muzoo.ssc.controlmap.web.dto.AdminPasswordResetRequest;
import io.muzoo.ssc.controlmap.web.dto.RoleChangeRequest;
import io.muzoo.ssc.controlmap.web.dto.UserSummary;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin user-management API (#admin). ADMIN-only — declarative method security ({@code @PreAuthorize})
 * is the server-side boundary (a wrong-role caller is 403, unauthenticated 401), not a hidden UI item.
 * Every mutation is audited and guarded against admin self-lockout (see {@link UserAdminService}).
 */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public List<UserSummary> list() {
        return userAdminService.list();
    }

    @PutMapping("/{id}/role")
    public UserSummary changeRole(@PathVariable Long id, @Valid @RequestBody RoleChangeRequest request, Principal principal) {
        return userAdminService.changeRole(principal.getName(), id, request.role());
    }

    @PutMapping("/{id}/active")
    public UserSummary setActive(@PathVariable Long id, @Valid @RequestBody ActiveChangeRequest request, Principal principal) {
        return userAdminService.setActive(principal.getName(), id, request.active());
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody AdminPasswordResetRequest request, Principal principal) {
        userAdminService.resetPassword(principal.getName(), id, request.newPassword());
    }
}
