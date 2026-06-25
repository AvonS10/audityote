package io.muzoo.ssc.controlmap.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.muzoo.ssc.controlmap.domain.Asset;
import io.muzoo.ssc.controlmap.domain.Control;
import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.domain.FindingControlMapping;
import io.muzoo.ssc.controlmap.domain.FindingStatus;
import io.muzoo.ssc.controlmap.domain.Framework;
import io.muzoo.ssc.controlmap.domain.Role;
import io.muzoo.ssc.controlmap.domain.Severity;
import io.muzoo.ssc.controlmap.domain.User;
import io.muzoo.ssc.controlmap.repository.ControlRepository;
import io.muzoo.ssc.controlmap.repository.FindingControlMappingRepository;
import io.muzoo.ssc.controlmap.repository.FindingRepository;
import io.muzoo.ssc.controlmap.repository.FrameworkRepository;
import io.muzoo.ssc.controlmap.repository.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent startup seeder. The compliance catalog (frameworks + controls) is read from the
 * {@code catalog/catalog.json} resource — editing that file is all it takes to add frameworks or
 * controls, no code change. Frameworks upsert by slug, controls by (framework, code) with their
 * title/description/category kept in sync with the JSON; demo users upsert by email. Safe on every
 * boot. Demo passwords are BCrypt-hashed via {@link PasswordEncoder}; raw passwords are never logged.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String CATALOG_RESOURCE = "catalog/catalog.json";
    private static final String FINDINGS_RESOURCE = "seed/findings.json";

    record Catalog(List<FrameworkSeed> frameworks) {
    }

    record FrameworkSeed(String slug, String name, String version, List<ControlSeed> controls) {
    }

    record ControlSeed(String code, String title, String category, String description) {
    }

    record SampleFindings(List<FindingSeed> findings) {
    }

    record FindingSeed(String reference, String title, String severity, double cvss, String status,
                       String asset, String description, List<List<String>> controls) {
    }

    private final FrameworkRepository frameworks;
    private final ControlRepository controls;
    private final UserRepository users;
    private final FindingRepository findingRepo;
    private final FindingControlMappingRepository mappingRepo;
    private final PasswordEncoder passwordEncoder;
    private final SeedProperties seedProperties;
    private final ObjectMapper objectMapper;

    public DataSeeder(FrameworkRepository frameworks, ControlRepository controls, UserRepository users,
                      FindingRepository findingRepo, FindingControlMappingRepository mappingRepo,
                      PasswordEncoder passwordEncoder, SeedProperties seedProperties, ObjectMapper objectMapper) {
        this.frameworks = frameworks;
        this.controls = controls;
        this.users = users;
        this.findingRepo = findingRepo;
        this.mappingRepo = mappingRepo;
        this.passwordEncoder = passwordEncoder;
        this.seedProperties = seedProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    /** Runs the full idempotent seed; exposed (not just via run) so it can be exercised in tests. */
    @Transactional
    public void seed() {
        Catalog catalog = loadCatalog();
        int frameworkCount = 0;
        int controlCount = 0;
        for (FrameworkSeed fw : catalog.frameworks()) {
            seedFramework(fw);
            frameworkCount++;
            controlCount += fw.controls().size();
        }

        int userCount = 0;
        userCount += seedUser(seedProperties.getAnalyst(), Role.ANALYST);
        userCount += seedUser(seedProperties.getReviewer(), Role.REVIEWER);

        int findingCount = seedSampleFindings();

        log.info("Seed complete: {} frameworks, {} controls from {}; {} demo users created; {} sample findings created.",
                frameworkCount, controlCount, CATALOG_RESOURCE, userCount, findingCount);
    }

    private Catalog loadCatalog() {
        try (InputStream in = new ClassPathResource(CATALOG_RESOURCE).getInputStream()) {
            return objectMapper.readValue(in, Catalog.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read seed catalog " + CATALOG_RESOURCE, e);
        }
    }

    /**
     * Seeds the demo findings (owned by the seeded Analyst) from {@code seed/findings.json}, with
     * their control mappings. Idempotent by reference; skipped if the Analyst user is not seeded.
     */
    private int seedSampleFindings() {
        SeedProperties.Account analyst = seedProperties.getAnalyst();
        if (analyst == null || isBlank(analyst.getEmail())) {
            return 0;
        }
        User owner = users.findByEmail(analyst.getEmail()).orElse(null);
        if (owner == null) {
            return 0;
        }

        SampleFindings sample;
        try (InputStream in = new ClassPathResource(FINDINGS_RESOURCE).getInputStream()) {
            sample = objectMapper.readValue(in, SampleFindings.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read sample findings " + FINDINGS_RESOURCE, e);
        }

        int created = 0;
        for (FindingSeed seed : sample.findings()) {
            if (findingRepo.existsByReference(seed.reference())) {
                continue;
            }
            Finding finding = new Finding(seed.reference(), seed.title(), seed.description(),
                    Severity.valueOf(seed.severity().toUpperCase(Locale.ROOT)),
                    BigDecimal.valueOf(seed.cvss()), owner, new Asset(seed.asset(), null, null, null));
            finding.setStatus(FindingStatus.valueOf(seed.status().toUpperCase(Locale.ROOT).replace('-', '_')));
            findingRepo.save(finding);

            for (List<String> ref : seed.controls()) {
                Control control = lookupControl(ref.get(0), ref.get(1));
                if (control != null) {
                    mappingRepo.save(new FindingControlMapping(finding, control));
                }
            }
            created++;
        }
        return created;
    }

    private Control lookupControl(String slug, String code) {
        return frameworks.findBySlug(slug)
                .flatMap(fw -> controls.findByFramework_IdAndCode(fw.getId(), code))
                .orElse(null);
    }

    /** Ensures a framework exists (by slug) and that each of its controls exists and matches the JSON. */
    private void seedFramework(FrameworkSeed seed) {
        Framework framework = frameworks.findBySlug(seed.slug())
                .orElseGet(() -> frameworks.save(new Framework(seed.slug(), seed.name(), seed.version())));

        for (ControlSeed c : seed.controls()) {
            Control existing = controls.findByFramework_IdAndCode(framework.getId(), c.code()).orElse(null);
            if (existing == null) {
                controls.save(new Control(framework, c.code(), c.title(), c.description(), c.category()));
            } else if (changed(existing, c)) {
                existing.setTitle(c.title());
                existing.setDescription(c.description());
                existing.setCategory(c.category());
                controls.save(existing);
            }
        }
    }

    private static boolean changed(Control existing, ControlSeed seed) {
        return !Objects.equals(existing.getTitle(), seed.title())
                || !Objects.equals(existing.getDescription(), seed.description())
                || !Objects.equals(existing.getCategory(), seed.category());
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
