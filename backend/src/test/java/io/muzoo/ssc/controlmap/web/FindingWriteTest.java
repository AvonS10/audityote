package io.muzoo.ssc.controlmap.web;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.muzoo.ssc.controlmap.domain.Asset;
import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.domain.FindingStatus;
import io.muzoo.ssc.controlmap.domain.Role;
import io.muzoo.ssc.controlmap.domain.Severity;
import io.muzoo.ssc.controlmap.domain.User;
import io.muzoo.ssc.controlmap.repository.FindingRepository;
import io.muzoo.ssc.controlmap.repository.UserRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finding create/edit/delete — the first write path. Verifies reference generation, the CVSS→severity
 * rule, validation, and the owner-only / editable-states authorization enforced server-side (§8/§10).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FindingWriteTest {

    private static final String OWNER = "owner@findwrite.test";
    private static final String OTHER = "other@findwrite.test";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private FindingRepository findings;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = users.save(new User(OWNER, "Owner Analyst", "x", Role.ANALYST));
        users.save(new User(OTHER, "Other Analyst", "x", Role.ANALYST));
    }

    private Finding seed(String reference, FindingStatus statusValue) {
        Finding f = findings.save(new Finding(reference, "Existing finding", "desc",
                Severity.HIGH, new BigDecimal("7.5"), owner, new Asset("sys", null, null, null)));
        f.setStatus(statusValue);
        return findings.save(f);
    }

    private static String body(String severityJson, String cvssJson) {
        return ("{\"title\":\"SQL injection in reports\",\"description\":\"d\","
                + "\"severity\":%s,\"cvss\":%s,\"asset\":{\"name\":\"reporting-svc\"}}").formatted(severityJson, cvssJson);
    }

    @Test
    @WithMockUser(username = OWNER)
    void createGeneratesReferenceOwnerAndOpenStatus() throws Exception {
        mockMvc.perform(post("/api/findings").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("\"high\"", "null")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference", matchesPattern("CM-\\d{4}-\\d{4}")))
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.severity").value("high"))
                .andExpect(jsonPath("$.owner").value("Owner Analyst"))
                .andExpect(jsonPath("$.asset.name").value("reporting-svc"))
                .andExpect(jsonPath("$.controls").isEmpty());
    }

    @Test
    @WithMockUser(username = OWNER)
    void createDerivesSeverityFromCvss() throws Exception {
        // CVSS 9.5 → critical, regardless of any severity sent.
        mockMvc.perform(post("/api/findings").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("\"low\"", "9.5")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.severity").value("critical"))
                .andExpect(jsonPath("$.cvss").value(9.5));
    }

    @Test
    @WithMockUser(username = OWNER)
    void createRequiresSeverityWhenNoCvss() throws Exception {
        mockMvc.perform(post("/api/findings").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("null", "null")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = OWNER)
    void createRejectsCvssOutOfRange() throws Exception {
        mockMvc.perform(post("/api/findings").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("null", "11.0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("cvss"));
    }

    @Test
    @WithMockUser(username = OWNER)
    void createRejectsCvssWithTwoDecimals() throws Exception {
        // 7.44 previously slipped through and NUMERIC(3,1) silently rounded it (P1 hardening).
        mockMvc.perform(post("/api/findings").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("null", "7.44")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("cvss"));
    }

    @Test
    @WithMockUser(username = OWNER)
    void editingOpenMovesItToInProgress() throws Exception {
        Finding f = seed("CM-TEST-W101", FindingStatus.OPEN);
        mockMvc.perform(put("/api/findings/" + f.getId()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("\"medium\"", "null")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("in-progress"))
                .andExpect(jsonPath("$.severity").value("medium"));
    }

    @Test
    @WithMockUser(username = OTHER)
    void nonOwnerCannotEdit() throws Exception {
        Finding f = seed("CM-TEST-W102", FindingStatus.OPEN);
        mockMvc.perform(put("/api/findings/" + f.getId()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("\"low\"", "null")))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = OWNER)
    void cannotEditInNonEditableState() throws Exception {
        Finding f = seed("CM-TEST-W103", FindingStatus.SUBMITTED);
        mockMvc.perform(put("/api/findings/" + f.getId()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("\"low\"", "null")))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = OWNER)
    void deleteSoftDeletesKeepingItReviewableButGoneFromList() throws Exception {
        Finding f = seed("CM-TEST-W104", FindingStatus.OPEN);
        mockMvc.perform(delete("/api/findings/" + f.getId()).with(csrf())).andExpect(status().isNoContent());
        // Soft-deleted: still retrievable read-only (so its audit trail survives), now flagged deleted.
        mockMvc.perform(get("/api/findings/" + f.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));
        // ...excluded from the active findings list...
        mockMvc.perform(get("/api/findings").param("q", "CM-TEST-W104"))
                .andExpect(jsonPath("$.totalElements").value(0));
        // ...but surfaced by the "show deleted" view.
        mockMvc.perform(get("/api/findings").param("deleted", "true").param("q", "CM-TEST-W104"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].reference").value("CM-TEST-W104"));
        // ...and further mutations treat it as gone.
        mockMvc.perform(delete("/api/findings/" + f.getId()).with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = OTHER)
    void nonOwnerCannotDelete() throws Exception {
        Finding f = seed("CM-TEST-W105", FindingStatus.OPEN);
        mockMvc.perform(delete("/api/findings/" + f.getId()).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = OWNER)
    void getUnknownReturns404() throws Exception {
        mockMvc.perform(get("/api/findings/999999")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = OWNER)
    void createWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/api/findings").contentType(MediaType.APPLICATION_JSON).content(body("\"high\"", "null")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUnauthenticatedIs401() throws Exception {
        mockMvc.perform(post("/api/findings").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("\"high\"", "null")))
                .andExpect(status().isUnauthorized());
    }
}
