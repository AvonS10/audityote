package io.muzoo.ssc.controlmap.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coverage rollup — finding counts, highest severity, at-risk gating, and framework validation.
 * Uses a throwaway framework + controls created in the (rolled-back) test transaction, so the
 * assertions are deterministic regardless of seeded or manually-created data in the shared dev DB.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CoverageControllerTest {

    private static final String ANALYST = "analyst@cov.test";
    private static final String FW = "cov-test-fw";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private FindingRepository findings;
    @Autowired private FrameworkRepository frameworks;
    @Autowired private ControlRepository controls;
    @Autowired private FindingControlMappingRepository mappings;

    private User owner;
    private Control riskControl;
    private Control safeControl;
    private Control gapControl;

    @BeforeEach
    void setUp() {
        owner = users.save(new User(ANALYST, "Analyst", "x", Role.ANALYST));
        Framework framework = frameworks.save(new Framework(FW, "Coverage Test Framework", "1.0"));
        riskControl = controls.save(new Control(framework, "CTRL-RISK", "Risky control", "d", null));
        safeControl = controls.save(new Control(framework, "CTRL-SAFE", "Covered control", "d", null));
        gapControl = controls.save(new Control(framework, "CTRL-GAP", "Uncovered control", "d", null));
    }

    private void map(String reference, Severity severity, FindingStatus statusValue, Control control) {
        Finding f = findings.save(new Finding(reference, "Finding", "d",
                severity, new BigDecimal("7.5"), owner, new Asset("sys", null, null, null)));
        f.setStatus(statusValue);
        findings.save(f);
        mappings.save(new FindingControlMapping(f, control));
    }

    @Test
    @WithMockUser(username = ANALYST)
    void coverageReportsCountSeverityAndAtRisk() throws Exception {
        // RISK: an active critical (at-risk) plus a low; SAFE: a remediated high (covered, NOT at-risk).
        map("CM-COV-1", Severity.CRITICAL, FindingStatus.OPEN, riskControl);
        map("CM-COV-2", Severity.LOW, FindingStatus.IN_PROGRESS, riskControl);
        map("CM-COV-3", Severity.HIGH, FindingStatus.REMEDIATED, safeControl);

        mockMvc.perform(get("/api/coverage").param("framework", FW))
                .andExpect(status().isOk())
                // CTRL-RISK — two findings, worst is critical, at-risk via the active critical.
                .andExpect(jsonPath("$[?(@.control.code=='CTRL-RISK')].findingCount").value(2))
                .andExpect(jsonPath("$[?(@.control.code=='CTRL-RISK')].highestSeverity").value("critical"))
                .andExpect(jsonPath("$[?(@.control.code=='CTRL-RISK')].atRisk").value(true))
                // CTRL-SAFE — one finding, but remediated, so not at-risk.
                .andExpect(jsonPath("$[?(@.control.code=='CTRL-SAFE')].findingCount").value(1))
                .andExpect(jsonPath("$[?(@.control.code=='CTRL-SAFE')].highestSeverity").value("high"))
                .andExpect(jsonPath("$[?(@.control.code=='CTRL-SAFE')].atRisk").value(false))
                // CTRL-GAP — unmapped gap: zero findings, null severity, not at-risk.
                .andExpect(jsonPath("$[?(@.control.code=='CTRL-GAP')].findingCount").value(0))
                .andExpect(jsonPath("$[?(@.control.code=='CTRL-GAP')].highestSeverity").value((Object) null))
                .andExpect(jsonPath("$[?(@.control.code=='CTRL-GAP')].atRisk").value(false));
    }

    @Test
    @WithMockUser(username = ANALYST)
    void unknownFrameworkIs404() throws Exception {
        mockMvc.perform(get("/api/coverage").param("framework", "nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/coverage").param("framework", FW))
                .andExpect(status().isUnauthorized());
    }
}
