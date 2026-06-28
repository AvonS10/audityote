package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.domain.Role;
import io.muzoo.ssc.controlmap.domain.User;
import io.muzoo.ssc.controlmap.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service registration (#reg). Gated by an email-domain allowlist so signup stays company-scoped
 * (the industry "domain-based auto-join" pattern): only addresses on a configured company domain may
 * register, and every new account is an {@link Role#ANALYST} — registration never self-grants REVIEWER
 * (role changes are an admin action). An empty allowlist means registration is disabled.
 */
@Service
@Transactional
public class RegistrationService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final List<String> allowedDomains;

    public RegistrationService(UserRepository users, PasswordEncoder passwordEncoder,
                               @Value("${controlmap.signup.allowed-domains:}") List<String> allowedDomains) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.allowedDomains = allowedDomains.stream()
                .map(d -> d.trim().toLowerCase(Locale.ROOT))
                .filter(d -> !d.isEmpty())
                .toList();
    }

    public User register(String name, String email, String password) {
        if (allowedDomains.isEmpty()) {
            throw new ForbiddenException("Self-registration is not enabled.");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (!allowedDomains.contains(domainOf(normalizedEmail))) {
            throw new ForbiddenException("Registration is restricted to approved company email domains.");
        }
        if (users.existsByEmail(normalizedEmail)) {
            throw new ConflictException("An account with that email already exists.");
        }
        User user = new User(normalizedEmail, name.trim(), passwordEncoder.encode(password), Role.ANALYST);
        return users.save(user);
    }

    private static String domainOf(String email) {
        int at = email.indexOf('@');
        // @Email validation guarantees a single '@'; guard defensively anyway.
        return at >= 0 ? email.substring(at + 1) : "";
    }
}
