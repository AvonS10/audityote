package io.muzoo.ssc.controlmap.seed;

import io.muzoo.ssc.controlmap.domain.Control;
import io.muzoo.ssc.controlmap.domain.Framework;
import io.muzoo.ssc.controlmap.domain.Role;
import io.muzoo.ssc.controlmap.domain.User;
import io.muzoo.ssc.controlmap.repository.ControlRepository;
import io.muzoo.ssc.controlmap.repository.FrameworkRepository;
import io.muzoo.ssc.controlmap.repository.UserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent startup seeder for the compliance catalog (frameworks + controls) and the demo
 * Analyst/Reviewer users. Safe to run on every boot: frameworks upsert by (name, version), controls
 * by (framework, code), users by email — so nothing is duplicated and existing rows are left intact.
 *
 * <p>Demo passwords are BCrypt-hashed via {@link PasswordEncoder}; raw passwords are never logged.
 * The control set mirrors the design system's catalog (PLAN §12 / §16: seed all three frameworks).
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private record ControlSeed(String code, String title) {
    }

    private static final String ISO_NAME = "ISO/IEC 27001";
    private static final String ISO_VERSION = "2022";
    private static final List<ControlSeed> ISO_CONTROLS = List.of(
            new ControlSeed("A.5.7", "Threat intelligence"),
            new ControlSeed("A.5.17", "Authentication information"),
            new ControlSeed("A.5.23", "Information security for cloud services"),
            new ControlSeed("A.5.30", "ICT readiness for business continuity"),
            new ControlSeed("A.6.3", "Information security awareness & training"),
            new ControlSeed("A.7.4", "Physical security monitoring"),
            new ControlSeed("A.8.5", "Secure authentication"),
            new ControlSeed("A.8.8", "Management of technical vulnerabilities"),
            new ControlSeed("A.8.15", "Logging"),
            new ControlSeed("A.8.16", "Monitoring activities"),
            new ControlSeed("A.8.23", "Web filtering"),
            new ControlSeed("A.8.24", "Use of cryptography"),
            new ControlSeed("A.8.25", "Secure development life cycle"),
            new ControlSeed("A.8.26", "Application security requirements"),
            new ControlSeed("A.8.28", "Secure coding"));

    private static final String OWASP_NAME = "OWASP Top 10";
    private static final String OWASP_VERSION = "2025";
    private static final List<ControlSeed> OWASP_CONTROLS = List.of(
            new ControlSeed("A01", "Broken access control"),
            new ControlSeed("A02", "Cryptographic failures"),
            new ControlSeed("A03", "Injection"),
            new ControlSeed("A04", "Insecure design"),
            new ControlSeed("A05", "Security misconfiguration"),
            new ControlSeed("A06", "Vulnerable & outdated components"),
            new ControlSeed("A07", "Identification & authentication failures"),
            new ControlSeed("A08", "Software & data integrity failures"),
            new ControlSeed("A09", "Security logging & monitoring failures"),
            new ControlSeed("A10", "Server-side request forgery (SSRF)"));

    private static final String NIST_NAME = "NIST CSF";
    private static final String NIST_VERSION = "2.0";
    private static final List<ControlSeed> NIST_CONTROLS = List.of(
            new ControlSeed("GV.OC", "Organizational context"),
            new ControlSeed("GV.RM", "Risk management strategy"),
            new ControlSeed("ID.AM", "Asset management"),
            new ControlSeed("ID.RA", "Risk assessment"),
            new ControlSeed("PR.AA", "Identity management, authentication & access control"),
            new ControlSeed("PR.DS", "Data security"),
            new ControlSeed("PR.PS", "Platform security"),
            new ControlSeed("PR.IR", "Technology infrastructure resilience"),
            new ControlSeed("DE.CM", "Continuous monitoring"),
            new ControlSeed("DE.AE", "Adverse event analysis"),
            new ControlSeed("RS.MA", "Incident management"),
            new ControlSeed("RC.RP", "Incident recovery plan execution"));

    private final FrameworkRepository frameworks;
    private final ControlRepository controls;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SeedProperties seedProperties;

    public DataSeeder(FrameworkRepository frameworks, ControlRepository controls, UserRepository users,
                      PasswordEncoder passwordEncoder, SeedProperties seedProperties) {
        this.frameworks = frameworks;
        this.controls = controls;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.seedProperties = seedProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    /** Runs the full idempotent seed; exposed (not just via run) so it can be exercised in tests. */
    @Transactional
    public void seed() {
        int frameworkCount = 0;
        int controlCount = 0;
        frameworkCount += seedFramework(ISO_NAME, ISO_VERSION, ISO_CONTROLS);
        controlCount += ISO_CONTROLS.size();
        frameworkCount += seedFramework(OWASP_NAME, OWASP_VERSION, OWASP_CONTROLS);
        controlCount += OWASP_CONTROLS.size();
        frameworkCount += seedFramework(NIST_NAME, NIST_VERSION, NIST_CONTROLS);
        controlCount += NIST_CONTROLS.size();

        int userCount = 0;
        userCount += seedUser(seedProperties.getAnalyst(), Role.ANALYST);
        userCount += seedUser(seedProperties.getReviewer(), Role.REVIEWER);

        log.info("Seed complete: {} frameworks ensured, {} controls ensured, {} demo users created.",
                frameworkCount, controlCount, userCount);
    }

    /** Ensures a framework and all its controls exist. Returns 1 if the framework was newly created. */
    private int seedFramework(String name, String version, List<ControlSeed> controlSeeds) {
        Framework framework = frameworks.findByNameAndVersion(name, version).orElse(null);
        int created = 0;
        if (framework == null) {
            framework = frameworks.save(new Framework(name, version));
            created = 1;
        }
        for (ControlSeed seed : controlSeeds) {
            if (controls.findByFramework_IdAndCode(framework.getId(), seed.code()).isEmpty()) {
                controls.save(new Control(framework, seed.code(), seed.title(), null));
            }
        }
        return created;
    }

    /** Creates a demo user if its credentials are configured and it does not already exist. */
    private int seedUser(SeedProperties.Account account, Role role) {
        if (account == null || isBlank(account.getEmail()) || isBlank(account.getPassword())) {
            log.warn("Seed user for role {} is not configured; skipping.", role);
            return 0;
        }
        if (users.existsByEmail(account.getEmail())) {
            return 0;
        }
        String name = isBlank(account.getName()) ? defaultName(role) : account.getName();
        users.save(new User(account.getEmail(), name,
                passwordEncoder.encode(account.getPassword()), role));
        log.info("Seeded demo {} user.", role);
        return 1;
    }

    private static String defaultName(Role role) {
        return role == Role.REVIEWER ? "Demo Reviewer" : "Demo Analyst";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
