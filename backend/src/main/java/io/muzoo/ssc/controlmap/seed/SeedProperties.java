package io.muzoo.ssc.controlmap.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Demo-user credentials for seeding, bound from the environment (e.g. {@code SEED_ANALYST_EMAIL}).
 * These are never committed (real values live in the gitignored {@code .env}); if blank, the
 * corresponding user is simply not seeded.
 */
@ConfigurationProperties(prefix = "seed")
public class SeedProperties {

    private Account analyst = new Account();
    private Account reviewer = new Account();

    public Account getAnalyst() {
        return analyst;
    }

    public void setAnalyst(Account analyst) {
        this.analyst = analyst;
    }

    public Account getReviewer() {
        return reviewer;
    }

    public void setReviewer(Account reviewer) {
        this.reviewer = reviewer;
    }

    /** A single seed account: email + raw password (hashed before persistence). */
    public static class Account {
        private String email;
        private String password;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
