package io.muzoo.ssc.controlmap.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Eagerly renders the {@link CsrfToken} on each request so {@code CookieCsrfTokenRepository} writes
 * the {@code XSRF-TOKEN} cookie — letting the SPA read it and echo it back on unsafe requests, even
 * on a plain GET that wouldn't otherwise touch the token.
 */
public final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken(); // triggers the repository to persist the token cookie
        }
        filterChain.doFilter(request, response);
    }
}
