package io.muzoo.ssc.controlmap.web;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Audit trail (Observer #16): create + transitions record AuditLog rows, exposed in FindingDetail.audit. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuditTrailTest {

    private static final String OWNER = "owner@audit.test";
    private static final String REVIEWER = "rev@audit.test";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private FindingRepository findings;
    @Autowired private FrameworkRepository frameworks;
    @Autowired private ControlRepository controls;
    @Autowired private FindingControlMappingRepository mappings;

    private User owner;
    private Control control;

    @BeforeEach
    void setUp() {
        owner = users.save(new User(OWNER, "Owner", "x", Role.ANALYST));
        users.save(new User(REVIEWER, "Reviewer", "x", Role.REVIEWER));
        Long owaspId = frameworks.findBySlug("owasp").orElseThrow().getId();
        control = controls.findByFramework_IdAndCode(owaspId, "A05").orElseThrow();
    }

    @Test
    @WithMockUser(username = OWNER)
    void createAndSubmitAreAudited() throws Exception {
        String created = mockMvc.perform(post("/api/findings").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Audit me\",\"severity\":\"high\",\"asset\":{\"name\":\"Edge\"}}"))
                .andExpect(status().isCreated())
                // The "created" entry is recorded at creation time by the Observer.
                .andExpect(jsonPath("$.audit[?(@.action=='created')].toStatus").value(hasItem("open")))
                .andExpect(jsonPath("$.audit[?(@.action=='created')].actor").value(hasItem("Owner")))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(created, "$.id");

        mockMvc.perform(post("/api/findings/" + id + "/controls").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"controlId\":" + control.getId() + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/findings/" + id + "/transition").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"submit\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("submitted"))
                .andExpect(jsonPath("$.audit[?(@.action=='created')]").exists())
                .andExpect(jsonPath("$.audit[?(@.action=='submit')].fromStatus").value(hasItem("open")))
                .andExpect(jsonPath("$.audit[?(@.action=='submit')].toStatus").value(hasItem("submitted")))
                .andExpect(jsonPath("$.audit[?(@.action=='submit')].actor").value(hasItem("Owner")));
    }

    @Test
    @WithMockUser(username = REVIEWER)
    void returnPersistsTheComment() throws Exception {
        Finding f = findings.save(new Finding("CM-AUD-1", "Finding", "d",
                Severity.HIGH, new BigDecimal("7.5"), owner, new Asset("sys", null, null, null)));
        f.setStatus(FindingStatus.SUBMITTED);
        findings.save(f);
        mappings.save(new FindingControlMapping(f, control));

        mockMvc.perform(post("/api/findings/" + f.getId() + "/transition").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"return\",\"comment\":\"Add NIST mappings.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("returned"))
                // The reviewer's return comment is persisted on the audit row.
                .andExpect(jsonPath("$.audit[?(@.action=='return')].comment").value(hasItem("Add NIST mappings.")))
                .andExpect(jsonPath("$.audit[?(@.action=='return')].fromStatus").value(hasItem("submitted")))
                .andExpect(jsonPath("$.audit[?(@.action=='return')].toStatus").value(hasItem("returned")))
                .andExpect(jsonPath("$.audit[?(@.action=='return')].actor").value(hasItem("Reviewer")));
    }
}
