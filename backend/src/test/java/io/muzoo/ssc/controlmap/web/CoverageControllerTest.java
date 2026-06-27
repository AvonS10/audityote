package io.muzoo.ssc.controlmap.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.muzoo.ssc.controlmap.domain.Asset;
import io.muzoo.ssc.controlmap.domain.Control;
import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.domain.FindingControlMapping;
import io.muzoo.ssc.controlmap.domain.FindingStatus;
import io.muzoo.ssc.controlmap.domain.Role;
import io.muzoo.ssc.controlmap.domain.Severity;
import io.muzoo.ssc.controlmap.domain.User;
import io.muzoo.ssc.controlmap.repository.ControlRepository;
import io.muzoo.ssc.controlmap.repository.FindingControlMappingRepository;
import io.muzoo.ssc.controlmap.repository.FindingRepository;
import io.muzoo.ssc.controlmap.repository.FrameworkRepository;
import io.muzoo.ssc.controlmap.repository.UserRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Coverage rollup — finding counts, highest severity, at-risk gating, and framework validation. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CoverageControllerTest {

    private static final String ANALYST = "analyst@cov.test";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private FindingRepository findings;
    @Autowired private FrameworkRepository frameworks;
    @Autowired private ControlRepository controls;
    @Autowired private FindingControlMappingRepository mappings;

    private User owner;
    private Long owaspId;

    @BeforeEach
    void setUp() {
        owner = users.save(new User(ANALYST, "Analyst", "x", Role.ANALYST));
        owaspId = frameworks.findBySlug("owasp").orElseThrow().getId();
    }

    private void map(String reference, Severity severity, FindingStatus statusValue, String controlCode) {
        Finding f = findings.save(new Finding(reference, "Finding", "d",
                severity, new BigDecimal("7.5"), owner, new Asset("sys", null, null, null)));
        f.setStatus(statusValue);
        findings.save(f);
        Control control = controls.findByFramework_IdAndCode(owaspId, controlCode).orElseThrow();
        mappings.save(new FindingControlMapping(f, control));
    }

    @Test
    @WithMockUser(username = ANALYST)
    void coverageReportsCountSeverityAndAtRisk() throws Exception {
        // Use controls the seeded sample findings never map to (A05/A06/A08), so counts are exact.
        // A05: an active critical (at-risk) plus a low; A06: a remediated high (covered, NOT at-risk).
        map("CM-COV-1", Severity.CRITICAL, FindingStatus.OPEN, "A05");
        map("CM-COV-2", Severity.LOW, FindingStatus.IN_PROGRESS, "A05");
        map("CM-COV-3", Severity.HIGH, FindingStatus.REMEDIATED, "A06");

        mockMvc.perform(get("/api/coverage").param("framework", "owasp"))
                .andExpect(status().isOk())
                // A05 — two findings, worst is critical, at-risk via the active critical.
                .andExpect(jsonPath("$[?(@.control.code=='A05')].findingCount").value(2))
                .andExpect(jsonPath("$[?(@.control.code=='A05')].highestSeverity").value("critical"))
                .andExpect(jsonPath("$[?(@.control.code=='A05')].atRisk").value(true))
                // A06 — one finding, but remediated, so not at-risk.
                .andExpect(jsonPath("$[?(@.control.code=='A06')].findingCount").value(1))
                .andExpect(jsonPath("$[?(@.control.code=='A06')].highestSeverity").value("high"))
                .andExpect(jsonPath("$[?(@.control.code=='A06')].atRisk").value(false))
                // A08 — unmapped gap: zero findings, null severity, not at-risk.
                .andExpect(jsonPath("$[?(@.control.code=='A08')].findingCount").value(0))
                .andExpect(jsonPath("$[?(@.control.code=='A08')].highestSeverity").value((Object) null))
                .andExpect(jsonPath("$[?(@.control.code=='A08')].atRisk").value(false));
    }

    @Test
    @WithMockUser(username = ANALYST)
    void unknownFrameworkIs404() throws Exception {
        mockMvc.perform(get("/api/coverage").param("framework", "nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/coverage").param("framework", "owasp"))
                .andExpect(status().isUnauthorized());
    }
}
