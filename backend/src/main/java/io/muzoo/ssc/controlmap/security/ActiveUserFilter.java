package io.muzoo.ssc.controlmap.security;

import io.muzoo.ssc.controlmap.domain.Role;
import io.muzoo.ssc.controlmap.domain.User;
import io.muzoo.ssc.controlmap.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Force-logout for stale sessions after an admin offboarding or role change (#admin). Spring Security
 * loads the enabled flag and granted authorities once, at login, and caches them for the life of the
 * session — so a mid-session deactivation or demotion would keep taking effect until the user
 * re-authenticates. This filter re-reads the account on each authenticated request and, if it has been
 * deactivated ({@link User#isActive()} is false) or its role no longer matches the session's granted
 * authority, clears the context, invalidates the session, and returns 401 (forcing a fresh login that
 * picks up the current state).
 */
public class ActiveUserFilter extends OncePerRequestFilter {

    private final UserRepository users;
    private final RestAuthenticationEntryPoint entryPoint;

    public ActiveUserFilter(UserRepository users, RestAuthenticationEntryPoint entryPoint) {
        this.users = users;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            // Reject only when the user positively exists and is now deactivated or demoted; a missing
            // user passes through (we never hard-delete accounts, so it cannot arise in production —
            // this also keeps @WithMockUser tests, whose username is not in the DB, green).
            User user = users.findByEmail(auth.getName()).orElse(null);
            if (user != null && (!user.isActive() || roleChanged(user, auth))) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                entryPoint.commence(request, response, new DisabledException("Session no longer valid."));
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * True when the session carries one of our application-role authorities that no longer matches the
     * account's current role (an admin-driven promotion or demotion since login). Only our own roles are
     * considered — any other authority (e.g. the {@code ROLE_USER} injected by {@code @WithMockUser} in
     * tests) is ignored, so this reacts to real role changes without disturbing the test harness.
     */
    private static boolean roleChanged(User user, Authentication auth) {
        String current = user.getRole().name();
        for (GrantedAuthority granted : auth.getAuthorities()) {
            String authority = granted.getAuthority();
            if (authority != null && authority.startsWith("ROLE_")) {
                String roleName = authority.substring("ROLE_".length());
                if (isApplicationRole(roleName) && !roleName.equals(current)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isApplicationRole(String name) {
        for (Role role : Role.values()) {
            if (role.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
