package io.muzoo.ssc.controlmap.web;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
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
 * Review queue ({@code GET /api/reviews/queue}, #17): lists only SUBMITTED findings and is gated to
 * the Reviewer role server-side ({@code @PreAuthorize}) — a wrong-role request is 403, an
 * unauthenticated one 401, regardless of the UI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewQueueTest {

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
        owner = users.save(new User("owner@rq.test", "Owner", "x", Role.ANALYST));
        Long owaspId = frameworks.findBySlug("owasp").orElseThrow().getId();
        control = controls.findByFramework_IdAndCode(owaspId, "A05").orElseThrow();
    }

    private Finding seed(String reference, FindingStatus statusValue) {
        Finding f = findings.save(new Finding(reference, "Finding " + reference, "d",
                Severity.HIGH, new BigDecimal("7.5"), owner, new Asset("sys", null, null, null)));
        f.setStatus(statusValue);
        findings.save(f);
        mappings.save(new FindingControlMapping(f, control));
        return f;
    }

    @Test
    @WithMockUser(roles = "REVIEWER")
    void reviewerSeesOnlySubmittedFindings() throws Exception {
        seed("CM-RQ-1", FindingStatus.SUBMITTED);
        seed("CM-RQ-2", FindingStatus.OPEN);
        seed("CM-RQ-3", FindingStatus.SUBMITTED);
        seed("CM-RQ-4", FindingStatus.APPROVED);

        // Robust against seeded sample data: assert the predicate + presence/absence, not an exact count.
        mockMvc.perform(get("/api/reviews/queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].status", everyItem(is("submitted"))))
                .andExpect(jsonPath("$[*].reference", hasItem("CM-RQ-1")))
                .andExpect(jsonPath("$[*].reference", hasItem("CM-RQ-3")))
                .andExpect(jsonPath("$[*].reference", not(hasItem("CM-RQ-2"))))
                .andExpect(jsonPath("$[*].reference", not(hasItem("CM-RQ-4"))));
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void analystIsForbidden() throws Exception {
        mockMvc.perform(get("/api/reviews/queue"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void unauthenticatedIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/reviews/queue"))
                .andExpect(status().isUnauthorized());
    }
}
