package io.muzoo.ssc.controlmap.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * CSRF request handler for a cookie-based SPA (the pattern from the Spring Security reference).
 * Tokens are written XOR-encoded into the body/attribute (BREACH protection) but the SPA sends the
 * raw token back via the {@code X-XSRF-TOKEN} header, which is resolved as-is. Paired with
 * {@code CookieCsrfTokenRepository.withHttpOnlyFalse()} so the SPA can read the {@code XSRF-TOKEN} cookie.
 */
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        this.xor.handle(request, response, csrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        // Header (SPA) carries the raw token; form bodies would carry the XOR-encoded one.
        boolean fromHeader = StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()));
        return (fromHeader ? this.plain : this.xor).resolveCsrfTokenValue(request, csrfToken);
    }
}
