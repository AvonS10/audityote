package io.muzoo.ssc.controlmap.web;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.muzoo.ssc.controlmap.domain.Asset;
import io.muzoo.ssc.controlmap.domain.Control;
import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.domain.FindingStatus;
import io.muzoo.ssc.controlmap.domain.Role;
import io.muzoo.ssc.controlmap.domain.Severity;
import io.muzoo.ssc.controlmap.domain.User;
import io.muzoo.ssc.controlmap.repository.ControlRepository;
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

/** Control mapping add/remove — owner-only, editable-states, with duplicate/unknown handling. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FindingMappingTest {

    private static final String OWNER = "owner@map.test";
    private static final String OTHER = "other@map.test";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private FindingRepository findings;
    @Autowired private FrameworkRepository frameworks;
    @Autowired private ControlRepository controls;

    private User owner;
    private Long controlId;

    @BeforeEach
    void setUp() {
        owner = users.save(new User(OWNER, "Owner", "x", Role.ANALYST));
        users.save(new User(OTHER, "Other", "x", Role.ANALYST));
        Long owaspId = frameworks.findBySlug("owasp").orElseThrow().getId();
        controlId = controls.findByFramework_IdAndCode(owaspId, "A03").orElseThrow().getId();
    }

    private Finding seed(String reference, FindingStatus statusValue) {
        Finding f = findings.save(new Finding(reference, "Finding", "d",
                Severity.HIGH, new BigDecimal("7.5"), owner, new Asset("sys", null, null, null)));
        f.setStatus(statusValue);
        return findings.save(f);
    }

    private String body(Long id) {
        return "{\"controlId\":" + id + "}";
    }

    @Test
    @WithMockUser(username = OWNER)
    void addControlReturnsUpdatedDetail() throws Exception {
        Finding f = seed("CM-TEST-M1", FindingStatus.OPEN);
        mockMvc.perform(post("/api/findings/" + f.getId() + "/controls").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(controlId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.controls[*].framework", hasItem("owasp")))
                .andExpect(jsonPath("$.controls[*].code", hasItem("A03")))
                .andExpect(jsonPath("$.controls[0].controlId").value(controlId));
    }

    @Test
    @WithMockUser(username = OWNER)
    void addingTheSameControlTwiceIs409() throws Exception {
        Finding f = seed("CM-TEST-M2", FindingStatus.OPEN);
        mockMvc.perform(post("/api/findings/" + f.getId() + "/controls").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(controlId))).andExpect(status().isOk());
        mockMvc.perform(post("/api/findings/" + f.getId() + "/controls").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(controlId))).andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = OWNER)
    void addingUnknownControlIs404() throws Exception {
        Finding f = seed("CM-TEST-M3", FindingStatus.OPEN);
        mockMvc.perform(post("/api/findings/" + f.getId() + "/controls").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(999999L)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = OTHER)
    void nonOwnerCannotAdd() throws Exception {
        Finding f = seed("CM-TEST-M4", FindingStatus.OPEN);
        mockMvc.perform(post("/api/findings/" + f.getId() + "/controls").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(controlId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = OWNER)
    void cannotMapInNonEditableState() throws Exception {
        Finding f = seed("CM-TEST-M5", FindingStatus.SUBMITTED);
        mockMvc.perform(post("/api/findings/" + f.getId() + "/controls").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(controlId)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = OWNER)
    void removeControlClearsTheMapping() throws Exception {
        Finding f = seed("CM-TEST-M6", FindingStatus.OPEN);
        mockMvc.perform(post("/api/findings/" + f.getId() + "/controls").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(controlId))).andExpect(status().isOk());
        mockMvc.perform(delete("/api/findings/" + f.getId() + "/controls/" + controlId).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.controls").isEmpty());
    }

    @Test
    @WithMockUser(username = OWNER)
    void removingUnmappedControlIs404() throws Exception {
        Finding f = seed("CM-TEST-M7", FindingStatus.OPEN);
        mockMvc.perform(delete("/api/findings/" + f.getId() + "/controls/" + controlId).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresAuthentication() throws Exception {
        Finding f = seed("CM-TEST-M8", FindingStatus.OPEN);
        mockMvc.perform(post("/api/findings/" + f.getId() + "/controls").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(controlId)))
                .andExpect(status().isUnauthorized());
    }
}
