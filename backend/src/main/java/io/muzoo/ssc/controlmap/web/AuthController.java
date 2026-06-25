package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.domain.User;
import io.muzoo.ssc.controlmap.repository.UserRepository;
import io.muzoo.ssc.controlmap.web.dto.LoginRequest;
import io.muzoo.ssc.controlmap.web.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints (PLAN §10). Login authenticates the JSON credentials and persists the
 * security context into the (cookie-backed) HTTP session; {@code /me} returns the current user.
 * Logout is handled by Spring Security's logout filter (configured in SecurityConfig) → 204.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager, UserRepository users) {
        this.authenticationManager = authenticationManager;
        this.users = users;
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse httpResponse) {
        // Throws AuthenticationException on bad credentials → handled as 401 by GlobalExceptionHandler.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return ResponseEntity.ok(UserResponse.from(currentUser(authentication.getName())));
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        // Endpoint requires authentication, so `authentication` is present here.
        return UserResponse.from(currentUser(authentication.getName()));
    }

    private User currentUser(String email) {
        return users.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }
}
