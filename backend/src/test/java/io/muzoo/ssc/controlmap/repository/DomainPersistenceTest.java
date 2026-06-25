package io.muzoo.ssc.controlmap.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.muzoo.ssc.controlmap.domain.Asset;
import io.muzoo.ssc.controlmap.domain.AuditLog;
import io.muzoo.ssc.controlmap.domain.Control;
import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.domain.FindingControlMapping;
import io.muzoo.ssc.controlmap.domain.FindingStatus;
import io.muzoo.ssc.controlmap.domain.Framework;
import io.muzoo.ssc.controlmap.domain.MappingSource;
import io.muzoo.ssc.controlmap.domain.Role;
import io.muzoo.ssc.controlmap.domain.Severity;
import io.muzoo.ssc.controlmap.domain.User;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * Repository slice test against the configured PostgreSQL (the running dev DB locally; a Postgres
 * service in CI) with Flyway applied. Because ddl-auto is `validate`, the context only starts if
 * every entity matches the V2 migration — so this both exercises the Spring Data repositories and
 * proves the schema and mappings agree. Each test runs in a rolled-back transaction.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DomainPersistenceTest {

    @Autowired private UserRepository users;
    @Autowired private FrameworkRepository frameworks;
    @Autowired private ControlRepository controls;
    @Autowired private FindingRepository findings;
    @Autowired private FindingControlMappingRepository mappings;
    @Autowired private AuditLogRepository auditLogs;

    @Test
    void persistsAndQueriesTheFullGraph() {
        User analyst = users.save(new User("analyst@controlmap.local", "$2a$bcrypt-hash", Role.ANALYST));
        Framework iso = frameworks.save(new Framework("ISO/IEC 27001", "2022"));
        Control secureCoding = controls.save(
                new Control(iso, "A.8.28", "Secure coding", "Apply secure coding principles."));

        Finding finding = findings.save(new Finding(
                "CM-2026-0001", "SQL injection in login", "Unparameterised query.",
                Severity.HIGH, new BigDecimal("7.5"), analyst,
                new Asset("auth-service", "prod", "LoginController", "https://app.example/login")));

        mappings.save(new FindingControlMapping(finding, secureCoding));
        auditLogs.save(new AuditLog(finding, analyst, "CREATE", null, FindingStatus.OPEN, null));

        // Custom finders resolve.
        assertThat(users.findByEmail("analyst@controlmap.local")).get()
                .extracting(User::getRole).isEqualTo(Role.ANALYST);
        assertThat(frameworks.findByNameAndVersion("ISO/IEC 27001", "2022")).isPresent();
        assertThat(controls.findByFramework_IdAndCode(iso.getId(), "A.8.28")).isPresent();
        assertThat(controls.findByFramework_Id(iso.getId())).hasSize(1);

        // Finding round-trips: embedded asset, CVSS, default status, lifecycle timestamps.
        Finding reloaded = findings.findByReference("CM-2026-0001").orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FindingStatus.OPEN);
        assertThat(reloaded.getCvssScore()).isEqualByComparingTo("7.5");
        assertThat(reloaded.getAsset().getName()).isEqualTo("auth-service");
        assertThat(reloaded.getAsset().getComponent()).isEqualTo("LoginController");
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();

        // Join + audit trail.
        List<FindingControlMapping> forFinding = mappings.findByFinding_Id(finding.getId());
        assertThat(forFinding).singleElement()
                .satisfies(m -> assertThat(m.getSource()).isEqualTo(MappingSource.MANUAL));
        assertThat(mappings.existsByFinding_IdAndControl_Id(finding.getId(), secureCoding.getId())).isTrue();

        List<AuditLog> trail = auditLogs.findByFinding_IdOrderByTimestampAsc(finding.getId());
        assertThat(trail).singleElement()
                .satisfies(a -> assertThat(a.getToStatus()).isEqualTo(FindingStatus.OPEN));
    }
}
