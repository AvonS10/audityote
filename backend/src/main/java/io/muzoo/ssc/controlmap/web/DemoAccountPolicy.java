package io.muzoo.ssc.controlmap.web;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Marks the published public demo accounts (credentials printed in the README and on the sign-in
 * screen) so a visitor cannot change their password or display name and lock every other visitor out.
 *
 * <p>The locked set is an environment allowlist ({@code controlmap.demo.locked-emails}) rather than a
 * column on {@code User}: being a "public demo account" is a per-deployment concern, not a domain
 * property (the same accounts are ordinary users in any other environment). This mirrors the
 * signup-domain allowlist pattern. Empty (the default) locks nothing, so local/dev and CI are
 * unaffected. Enforcement is server-side in {@link AccountService}; admin password-reset
 * ({@link UserAdminService}) deliberately does not consult this, so it stays the recovery path.
 */
@Component
public class DemoAccountPolicy {

    private final Set<String> lockedEmails;

    public DemoAccountPolicy(@Value("${controlmap.demo.locked-emails:}") List<String> lockedEmails) {
        this.lockedEmails = lockedEmails.stream()
                .map(DemoAccountPolicy::normalize)
                .filter(e -> !e.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** True if the given account is a locked public demo account. Null-safe; matching is case-insensitive. */
    public boolean isLocked(String email) {
        return email != null && lockedEmails.contains(normalize(email));
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
