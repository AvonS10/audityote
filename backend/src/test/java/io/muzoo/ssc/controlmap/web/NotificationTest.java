package io.muzoo.ssc.controlmap.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.muzoo.ssc.controlmap.domain.Asset;
import io.muzoo.ssc.controlmap.domain.AuditLog;
import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.domain.FindingStatus;
import io.muzoo.ssc.controlmap.domain.Role;
import io.muzoo.ssc.controlmap.domain.Severity;
import io.muzoo.ssc.controlmap.domain.User;
import io.muzoo.ssc.controlmap.repository.AuditLogRepository;
import io.muzoo.ssc.controlmap.repository.FindingRepository;
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
 * Return-notifications (#4): GET /api/notifications is scoped to the caller and carries the reviewer's
 * comment; the dashboard {@code ?mine=true} filter restricts the list to the caller's own findings.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationTest {

    private static final String OWNER = "owner@notif.test";
    private static final String REVIEWER = "rev@notif.test";
    private static final String OTHER = "other@notif.test";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private FindingRepository findings;
    @Autowired private AuditLogRepository auditLog;

    private User owner;
    private User reviewer;
    private User other;

    @BeforeEach
    void setUp() {
        owner = users.save(new User(OWNER, "Owner", "x", Role.ANALYST));
        reviewer = users.save(new User(REVIEWER, "Reviewer", "x", Role.REVIEWER));
        other = users.save(new User(OTHER, "Other", "x", Role.ANALYST));
    }

    private Finding returned(String reference, User findingOwner, String comment) {
        Finding f = findings.save(new Finding(reference, "Finding " + reference, "d",
                Severity.HIGH, new BigDecimal("7.5"), findingOwner, new Asset("sys", null, null, null)));
        f.setStatus(FindingStatus.RETURNED);
        findings.save(f);
        auditLog.save(new AuditLog(f, reviewer, "return", FindingStatus.SUBMITTED, FindingStatus.RETURNED, comment));
        return f;
    }

    @Test
    @WithMockUser(username = OWNER)
    void ownerSeesOwnReturnedFindingWithComment() throws Exception {
        returned("CM-N-1", owner, "Please add NIST mappings.");
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reference").value("CM-N-1"))
                .andExpect(jsonPath("$[0].comment").value("Please add NIST mappings."))
                .andExpect(jsonPath("$[0].returnedBy").value("Reviewer"));
    }

    @Test
    @WithMockUser(username = OTHER)
    void otherUserSeesNoneOfOwnersReturns() throws Exception {
        returned("CM-N-2", owner, "Not yours.");
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = OWNER)
    void mineFilterReturnsOnlyCallersFindings() throws Exception {
        returned("CM-N-3", owner, "Mine.");
        returned("CM-N-4", other, "Someone else's.");
        mockMvc.perform(get("/api/findings?mine=true&status=returned"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].reference", hasItem("CM-N-3")))
                .andExpect(jsonPath("$.content[*].reference", not(hasItem("CM-N-4"))));
    }
}
