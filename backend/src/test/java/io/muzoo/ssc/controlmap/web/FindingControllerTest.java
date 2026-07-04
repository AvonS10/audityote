package io.muzoo.ssc.controlmap.web;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
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

/**
 * Findings list API. Creates its own findings in the test transaction (rolled back) and uses a
 * unique search token, so assertions hold regardless of any seeded sample data. Covers filtering,
 * the status/severity casing translation, the mapped-control refs, and 400/401.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FindingControllerTest {

    private static final String TOKEN = "zzqxsearchtoken";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private FrameworkRepository frameworks;
    @Autowired private ControlRepository controls;
    @Autowired private FindingRepository findings;
    @Autowired private FindingControlMappingRepository mappings;

    @BeforeEach
    void createFindings() {
        User owner = users.save(new User("findings-test@controlmap.test", "Findings Tester", "x", Role.ANALYST));

        Finding alpha = findings.save(new Finding("CM-TEST-9001", TOKEN + " alpha", "desc",
                Severity.CRITICAL, new BigDecimal("9.5"), owner, new Asset("Alpha System", null, null, null)));
        alpha.setStatus(FindingStatus.SUBMITTED);
        findings.save(alpha);

        Long owaspFwId = frameworks.findBySlug("owasp").orElseThrow().getId();
        Control a03 = controls.findByFramework_IdAndCode(owaspFwId, "A03").orElseThrow();
        mappings.save(new FindingControlMapping(alpha, a03));

        Finding beta = findings.save(new Finding("CM-TEST-9002", TOKEN + " beta", "desc",
                Severity.LOW, null, owner, new Asset("Beta System", null, null, null)));
        beta.setStatus(FindingStatus.IN_PROGRESS);
        findings.save(beta);
    }

    @Test
    @WithMockUser
    void listsWithCasingAndControlRefs() throws Exception {
        mockMvc.perform(get("/api/findings").param("q", TOKEN + " alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].reference").value("CM-TEST-9001"))
                .andExpect(jsonPath("$.content[0].severity").value("critical"))
                .andExpect(jsonPath("$.content[0].status").value("submitted"))
                .andExpect(jsonPath("$.content[0].asset").value("Alpha System"))
                .andExpect(jsonPath("$.content[0].controls[*].framework", hasItem("owasp")))
                .andExpect(jsonPath("$.content[0].controls[*].code", hasItem("A03")));
    }

    @Test
    @WithMockUser
    void listsWithoutAnyFilters() throws Exception {
        mockMvc.perform(get("/api/findings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].reference", hasItem("CM-TEST-9001")));
    }

    @Test
    @WithMockUser
    void translatesUnderscoreStatusToKebab() throws Exception {
        mockMvc.perform(get("/api/findings").param("q", TOKEN + " beta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("in-progress"));
    }

    @Test
    @WithMockUser
    void filtersByStatusUsingKebabInput() throws Exception {
        mockMvc.perform(get("/api/findings").param("status", "in-progress").param("q", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[*].status", everyItem(is("in-progress"))))
                .andExpect(jsonPath("$.content[0].reference").value("CM-TEST-9002"));
    }

    @Test
    @WithMockUser
    void filtersBySeverity() throws Exception {
        mockMvc.perform(get("/api/findings").param("severity", "critical").param("q", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[*].severity", everyItem(is("critical"))));
    }

    @Test
    @WithMockUser
    void filtersByFramework() throws Exception {
        mockMvc.perform(get("/api/findings").param("framework", "owasp").param("q", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].reference").value("CM-TEST-9001"));
    }

    @Test
    @WithMockUser
    void invalidStatusReturns400() throws Exception {
        mockMvc.perform(get("/api/findings").param("status", "bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithMockUser
    void negativePageReturns400NotServerError() throws Exception {
        // Without the @Min(0) bound this reached PageRequest.of and surfaced as a 500 (P1 hardening).
        mockMvc.perform(get("/api/findings").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser
    void oversizedPageSizeReturns400() throws Exception {
        mockMvc.perform(get("/api/findings").param("size", "2000000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithMockUser
    void zeroPageSizeReturns400() throws Exception {
        mockMvc.perform(get("/api/findings").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void maxPageSizeIsAccepted() throws Exception {
        mockMvc.perform(get("/api/findings").param("size", "100"))
                .andExpect(status().isOk());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/findings")).andExpect(status().isUnauthorized());
    }
}
