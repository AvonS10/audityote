package io.muzoo.ssc.controlmap.seed;

import static org.assertj.core.api.Assertions.assertThat;

import io.muzoo.ssc.controlmap.domain.Framework;
import io.muzoo.ssc.controlmap.domain.Role;
import io.muzoo.ssc.controlmap.domain.User;
import io.muzoo.ssc.controlmap.repository.ControlRepository;
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

    private DataSeeder newSeeder() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        SeedProperties props = new SeedProperties();
        props.getAnalyst().setEmail("analyst@controlmap.test");
        props.getAnalyst().setPassword("analyst-secret");
        props.getReviewer().setEmail("reviewer@controlmap.test");
        props.getReviewer().setPassword("reviewer-secret");
        return new DataSeeder(frameworks, controls, users, encoder, props);
    }

    @Test
    void seedsCatalogAndDemoUsers() {
        newSeeder().seed();

        // All three frameworks, with the expected number of controls each.
        Framework iso = frameworks.findByNameAndVersion("ISO/IEC 27001", "2022").orElseThrow();
        Framework owasp = frameworks.findByNameAndVersion("OWASP Top 10", "2025").orElseThrow();
        Framework nist = frameworks.findByNameAndVersion("NIST CSF", "2.0").orElseThrow();
        assertThat(controls.findByFramework_Id(iso.getId())).hasSize(15);
        assertThat(controls.findByFramework_Id(owasp.getId())).hasSize(10);
        assertThat(controls.findByFramework_Id(nist.getId())).hasSize(12);
        assertThat(controls.findByFramework_IdAndCode(iso.getId(), "A.8.28")).isPresent();

        // Demo user is created with a BCrypt hash (not plaintext) and the right role.
        User analyst = users.findByEmail("analyst@controlmap.test").orElseThrow();
        assertThat(analyst.getRole()).isEqualTo(Role.ANALYST);
        assertThat(analyst.getPasswordHash()).isNotEqualTo("analyst-secret").startsWith("$2");
        assertThat(new BCryptPasswordEncoder().matches("analyst-secret", analyst.getPasswordHash())).isTrue();
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
