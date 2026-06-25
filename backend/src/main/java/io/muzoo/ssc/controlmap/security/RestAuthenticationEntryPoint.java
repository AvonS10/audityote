package io.muzoo.ssc.controlmap.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.muzoo.ssc.controlmap.web.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Returns a JSON {@code 401} (not a login-page redirect) when an unauthenticated request hits a
 * protected endpoint, so the SPA can react by redirecting to {@code login?reason=expired} (§7.11).
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = ApiError.of(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", "Authentication required.");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
