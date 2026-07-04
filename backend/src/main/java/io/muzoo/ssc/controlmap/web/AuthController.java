package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.domain.User;
import io.muzoo.ssc.controlmap.repository.UserRepository;
import io.muzoo.ssc.controlmap.web.dto.LoginRequest;
import io.muzoo.ssc.controlmap.web.dto.RegisterRequest;
import io.muzoo.ssc.controlmap.web.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    private final RegistrationService registrationService;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager, UserRepository users,
                          RegistrationService registrationService) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.registrationService = registrationService;
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse httpResponse) {
        // Throws AuthenticationException on bad credentials → handled as 401 by GlobalExceptionHandler.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        startSession(authentication, httpRequest, httpResponse);
        return ResponseEntity.ok(UserResponse.from(currentUser(authentication.getName())));
    }

    /**
     * Self-service registration (domain-gated → ANALYST), then auto-login so the new user lands signed
     * in. Public endpoint; the service rejects disallowed domains (403) and duplicate emails (409).
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest httpRequest,
                                                 HttpServletResponse httpResponse) {
        User created = registrationService.register(request.name(), request.email(), request.password());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(created.getEmail(), request.password()));
        startSession(authentication, httpRequest, httpResponse);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(created));
    }

    /** Persist the authenticated security context into the (cookie-backed) HTTP session. */
    private void startSession(Authentication authentication, HttpServletRequest req, HttpServletResponse res) {
        // Session-fixation defense: rotate the session id at authentication so any id an attacker may have
        // fixed on the victim before login is discarded. We authenticate in this controller rather than
        // through a Spring Security authentication filter, so the framework's built-in rotation does not
        // fire — we do it explicitly. Only an existing session can be rotated; if none exists, saveContext
        // below mints a fresh (already-unfixated) one.
        if (req.getSession(false) != null) {
            req.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, req, res);
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
