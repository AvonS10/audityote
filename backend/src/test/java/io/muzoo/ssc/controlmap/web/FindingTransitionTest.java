package io.muzoo.ssc.controlmap.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Workflow transitions (State machine §8): legal advances, role/SoD gating, comment + submit guards. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FindingTransitionTest {

    private static final String OWNER = "owner@wf.test";
    private static final String REVIEWER = "rev@wf.test";
    private static final String OTHER = "other@wf.test";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private FindingRepository findings;
    @Autowired private FrameworkRepository frameworks;
    @Autowired private ControlRepository controls;
    @Autowired private FindingControlMappingRepository mappings;

    private User owner;
    private User reviewer;
    private Control control;

    @BeforeEach
    void setUp() {
        owner = users.save(new User(OWNER, "Owner", "x", Role.ANALYST));
        reviewer = users.save(new User(REVIEWER, "Reviewer", "x", Role.REVIEWER));
        users.save(new User(OTHER, "Other", "x", Role.ANALYST));
        Long owaspId = frameworks.findBySlug("owasp").orElseThrow().getId();
        control = controls.findByFramework_IdAndCode(owaspId, "A05").orElseThrow();
    }

    private Finding seed(String reference, FindingStatus statusValue, User findingOwner, boolean withControl) {
        Finding f = findings.save(new Finding(reference, "Finding", "d",
                Severity.HIGH, new BigDecimal("7.5"), findingOwner, new Asset("sys", null, null, null)));
        f.setStatus(statusValue);
        findings.save(f);
        if (withControl) {
            mappings.save(new FindingControlMapping(f, control));
        }
        return f;
    }

    private void transition(Long id, String body, int expectedStatus, String expectedWireStatus) throws Exception {
        var result = mockMvc.perform(post("/api/findings/" + id + "/transition").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus));
        if (expectedWireStatus != null) {
            result.andExpect(jsonPath("$.status").value(expectedWireStatus));
        }
    }

    // ---- happy paths ----

    @Test
    @WithMockUser(username = OWNER)
    void ownerSubmitsMappedFinding() throws Exception {
        Finding f = seed("CM-WF-1", FindingStatus.OPEN, owner, true);
        transition(f.getId(), "{\"action\":\"submit\"}", 200, "submitted");
    }

    @Test
    @WithMockUser(username = REVIEWER)
    void reviewerApprovesSubmittedFinding() throws Exception {
        Finding f = seed("CM-WF-2", FindingStatus.SUBMITTED, owner, true);
        transition(f.getId(), "{\"action\":\"approve\"}", 200, "approved");
    }

    @Test
    @WithMockUser(username = REVIEWER)
    void reviewerReturnsWithComment() throws Exception {
        Finding f = seed("CM-WF-3", FindingStatus.SUBMITTED, owner, true);
        transition(f.getId(), "{\"action\":\"return\",\"comment\":\"Please add NIST mappings.\"}", 200, "returned");
    }

    @Test
    @WithMockUser(username = OWNER)
    void ownerResubmitsReturnedFinding() throws Exception {
        Finding f = seed("CM-WF-4", FindingStatus.RETURNED, owner, true);
        transition(f.getId(), "{\"action\":\"resubmit\"}", 200, "submitted");
    }

    @Test
    @WithMockUser(username = OWNER)
    void ownerRemediatesApprovedFinding() throws Exception {
        Finding f = seed("CM-WF-5", FindingStatus.APPROVED, owner, true);
        transition(f.getId(), "{\"action\":\"remediate\"}", 200, "remediated");
    }

    @Test
    @WithMockUser(username = REVIEWER)
    void reviewerAcceptsRiskOnApprovedFinding() throws Exception {
        Finding f = seed("CM-WF-6", FindingStatus.APPROVED, owner, true);
        transition(f.getId(), "{\"action\":\"accept\"}", 200, "accepted");
    }

    @Test
    @WithMockUser(username = OWNER)
    void ownerReopensRemediatedFinding() throws Exception {
        Finding f = seed("CM-WF-7", FindingStatus.REMEDIATED, owner, true);
        transition(f.getId(), "{\"action\":\"reopen\"}", 200, "in-progress");
    }

    // ---- guards ----

    @Test
    @WithMockUser(username = OWNER)
    void submitWithoutMappedControlIs409() throws Exception {
        Finding f = seed("CM-WF-8", FindingStatus.OPEN, owner, false);
        transition(f.getId(), "{\"action\":\"submit\"}", 409, null);
    }

    @Test
    @WithMockUser(username = REVIEWER)
    void returnWithoutCommentIs400() throws Exception {
        Finding f = seed("CM-WF-9", FindingStatus.SUBMITTED, owner, true);
        transition(f.getId(), "{\"action\":\"return\"}", 400, null);
    }

    @Test
    @WithMockUser(username = REVIEWER)
    void illegalTransitionForStateIs409() throws Exception {
        // Approve is not legal from OPEN.
        Finding f = seed("CM-WF-10", FindingStatus.OPEN, owner, true);
        transition(f.getId(), "{\"action\":\"approve\"}", 409, null);
    }

    @Test
    @WithMockUser(username = OWNER)
    void analystCannotApproveIs403() throws Exception {
        Finding f = seed("CM-WF-11", FindingStatus.SUBMITTED, owner, true);
        transition(f.getId(), "{\"action\":\"approve\"}", 403, null);
    }

    @Test
    @WithMockUser(username = REVIEWER)
    void reviewerCannotApproveOwnFindingIs403() throws Exception {
        // Separation of duties: the reviewer owns this finding, so cannot decide on it.
        Finding f = seed("CM-WF-12", FindingStatus.SUBMITTED, reviewer, true);
        transition(f.getId(), "{\"action\":\"approve\"}", 403, null);
    }

    @Test
    @WithMockUser(username = OTHER)
    void nonOwnerCannotSubmitIs403() throws Exception {
        Finding f = seed("CM-WF-13", FindingStatus.OPEN, owner, true);
        transition(f.getId(), "{\"action\":\"submit\"}", 403, null);
    }

    @Test
    @WithMockUser(username = OWNER)
    void unknownActionIs400() throws Exception {
        Finding f = seed("CM-WF-14", FindingStatus.OPEN, owner, true);
        transition(f.getId(), "{\"action\":\"frobnicate\"}", 400, null);
    }

    @Test
    void requiresAuthentication() throws Exception {
        Finding f = seed("CM-WF-15", FindingStatus.OPEN, owner, true);
        transition(f.getId(), "{\"action\":\"submit\"}", 401, null);
    }
}
