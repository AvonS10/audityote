package io.muzoo.ssc.controlmap.security;

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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Force-logout for deactivated accounts (#admin offboarding). The {@code disabled} flag on
 * {@link AppUserDetailsService} blocks new logins, but an already-open session caches the old
 * enabled state — so this filter re-checks {@link User#isActive()} on each authenticated request and,
 * if the account has been deactivated, clears the context, invalidates the session, and returns 401.
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
            // Reject only when the user positively exists and is deactivated; a missing user passes
            // through (we never hard-delete accounts, so it cannot arise in production).
            boolean active = users.findByEmail(auth.getName()).map(User::isActive).orElse(true);
            if (!active) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                entryPoint.commence(request, response, new DisabledException("Account deactivated."));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
