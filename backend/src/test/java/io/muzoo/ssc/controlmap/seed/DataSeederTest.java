package io.muzoo.ssc.controlmap.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.muzoo.ssc.controlmap.domain.Control;
import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.domain.FindingStatus;
import io.muzoo.ssc.controlmap.domain.Framework;
import io.muzoo.ssc.controlmap.domain.Role;
import io.muzoo.ssc.controlmap.domain.Severity;
import io.muzoo.ssc.controlmap.domain.User;
import io.muzoo.ssc.controlmap.repository.AuditLogRepository;
import io.muzoo.ssc.controlmap.repository.ControlRepository;
import io.muzoo.ssc.controlmap.repository.FindingControlMappingRepository;
import io.muzoo.ssc.controlmap.repository.FindingRepository;
import io.muzoo.ssc.controlmap.repository.FrameworkRepository;
import io.muzoo.ssc.controlmap.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Verifies the seeder populates the catalog + demo users and is safe to run repeatedly. Uses the
 * real repositories (Postgres via @DataJpaTest) and drives {@link DataSeeder#seed()} directly.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DataSeederTest {

    @Autowired private FrameworkRepository frameworks;
    @Autowired private ControlRepository controls;
    @Autowired private UserRepository users;
    @Autowired private FindingRepository findingRepo;
    @Autowired private FindingControlMappingRepository mappingRepo;
    @Autowired private AuditLogRepository auditLogs;

    private DataSeeder newSeeder() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        SeedProperties props = new SeedProperties();
        props.getAnalyst().setEmail("analyst@controlmap.test");
        props.getAnalyst().setPassword("analyst-secret");
        props.getReviewer().setEmail("reviewer@controlmap.test");
        props.getReviewer().setPassword("reviewer-secret");
        return new DataSeeder(frameworks, controls, users, findingRepo, mappingRepo, encoder, props, new ObjectMapper());
    }

    @Test
    void seedsCatalogAndDemoUsers() {
        // Hermetic: the shared dev DB may hold workflow-mutated sample findings (the seeder skips by
        // reference, so a moved status would stick). Clear findings first so the seeder re-creates the
        // samples with their seeded statuses. Rolled back by @DataJpaTest — the dev DB is untouched.
        auditLogs.deleteAllInBatch();
        mappingRepo.deleteAllInBatch();
        findingRepo.deleteAllInBatch();

        newSeeder().seed();

        // All three frameworks (by slug), with the expected number of controls each.
        Framework iso = frameworks.findBySlug("iso27001").orElseThrow();
        Framework owasp = frameworks.findBySlug("owasp").orElseThrow();
        Framework nist = frameworks.findBySlug("nist").orElseThrow();
        assertThat(controls.findByFramework_Id(iso.getId())).hasSize(15);
        assertThat(controls.findByFramework_Id(owasp.getId())).hasSize(10);
        assertThat(controls.findByFramework_Id(nist.getId())).hasSize(12);

        // Controls carry the title/category/description from the JSON catalog.
        Control secureCoding = controls.findByFramework_IdAndCode(iso.getId(), "A.8.28").orElseThrow();
        assertThat(secureCoding.getTitle()).isEqualTo("Secure coding");
        assertThat(secureCoding.getCategory()).isEqualTo("Technological");
        assertThat(secureCoding.getDescription()).contains("Secure coding principles");

        // Demo user is created with a BCrypt hash (not plaintext) and the right role.
        User analyst = users.findByEmail("analyst@controlmap.test").orElseThrow();
        assertThat(analyst.getRole()).isEqualTo(Role.ANALYST);
        assertThat(analyst.getPasswordHash()).isNotEqualTo("analyst-secret").startsWith("$2");
        assertThat(new BCryptPasswordEncoder().matches("analyst-secret", analyst.getPasswordHash())).isTrue();

        // Sample findings are seeded (owned by an Analyst) with their control mappings.
        Finding xss = findingRepo.findByReference("CM-2025-0481").orElseThrow();
        assertThat(xss.getSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(xss.getStatus()).isEqualTo(FindingStatus.IN_PROGRESS);
        assertThat(xss.getOwner()).isNotNull();
        assertThat(mappingRepo.findByFinding_Id(xss.getId())).hasSize(2);
    }

    @Test
    void isIdempotentAcrossRepeatedRuns() {
        newSeeder().seed();
        long frameworksAfterFirst = frameworks.count();
        long controlsAfterFirst = controls.count();
        long usersAfterFirst = users.count();

        newSeeder().seed();

        assertThat(frameworks.count()).isEqualTo(frameworksAfterFirst);
        assertThat(controls.count()).isEqualTo(controlsAfterFirst);
        assertThat(users.count()).isEqualTo(usersAfterFirst);
    }
}
