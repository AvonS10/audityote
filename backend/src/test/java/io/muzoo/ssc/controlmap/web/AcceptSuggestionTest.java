package io.muzoo.ssc.controlmap.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.muzoo.ssc.controlmap.ai.ControlSuggestion;
import io.muzoo.ssc.controlmap.ai.MappingSuggestionStrategy;
import io.muzoo.ssc.controlmap.domain.Asset;
import io.muzoo.ssc.controlmap.domain.Control;
import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.domain.FindingControlMapping;
import io.muzoo.ssc.controlmap.domain.FindingStatus;
import io.muzoo.ssc.controlmap.domain.MappingSource;
import io.muzoo.ssc.controlmap.domain.Role;
import io.muzoo.ssc.controlmap.domain.Severity;
import io.muzoo.ssc.controlmap.domain.User;
import io.muzoo.ssc.controlmap.repository.ControlRepository;
import io.muzoo.ssc.controlmap.repository.FindingControlMappingRepository;
import io.muzoo.ssc.controlmap.repository.FindingRepository;
import io.muzoo.ssc.controlmap.repository.FrameworkRepository;
import io.muzoo.ssc.controlmap.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accepting an AI suggestion (S2b) — the server-authoritative provenance path. A mocked strategy stands
 * in for Claude (no live call). Proves that accepted provenance comes from the server's cached suggestion
 * (not the request), that a control the server never suggested can't be accepted, that the manual mapping
 * endpoint can't forge AI provenance, and that owner auth holds.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AcceptSuggestionTest {

    private static final String OWNER = "owner@accept.test";
    private static final String OTHER = "other@accept.test";

    @MockitoBean
    private MappingSuggestionStrategy strategy;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private FindingRepository findings;
    @Autowired private FrameworkRepository frameworks;
    @Autowired private ControlRepository controls;
    @Autowired private FindingControlMappingRepository mappings;

    private User owner;
    private Control a03;

    @BeforeEach
    void setUp() {
        owner = users.save(new User(OWNER, "Owner", "x", Role.ANALYST));
        users.save(new User(OTHER, "Other", "x", Role.ANALYST));
        Long owaspId = frameworks.findBySlug("owasp").orElseThrow().getId();
        a03 = controls.findByFramework_IdAndCode(owaspId, "A03").orElseThrow();
    }

    private Finding seed(String reference) {
        return findings.save(new Finding(reference, "SQL injection", "concatenated query",
                Severity.HIGH, new BigDecimal("7.5"), owner, new Asset("web", null, null, null)));
    }

    private String body(Long controlId) {
        return "{\"controlId\":" + controlId + "}";
    }

    @Test
    @WithMockUser(username = OWNER)
    void acceptStampsServerAuthoritativeProvenanceAndAuditsIt() throws Exception {
        // The server's own suggestion carries the confidence/rationale (standing in for Claude's output).
        when(strategy.suggest(any(), any()))
                .thenReturn(List.of(new ControlSuggestion(a03, 0.87, "Injection category.")));
        Finding f = seed("CM-AC-1");

        // 1) Get suggestions (populates the server-side cache).
        mockMvc.perform(post("/api/findings/" + f.getId() + "/suggest-controls").with(csrf()))
                .andExpect(status().isOk());

        // 2) Accept — the client sends ONLY the controlId, no provenance.
        mockMvc.perform(post("/api/findings/" + f.getId() + "/accept-suggestion").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(a03.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.controls[*].code", hasItem("A03")));

        // 3) Provenance is server-authoritative: source + confidence + rationale from the cache, model from config.
        FindingControlMapping mapping = mappings.findByFinding_IdAndControl_Id(f.getId(), a03.getId()).orElseThrow();
        assertThat(mapping.getSource()).isEqualTo(MappingSource.AI_SUGGESTED);
        assertThat(mapping.getAiConfidence()).isEqualTo(0.87);
        assertThat(mapping.getAiRationale()).isEqualTo("Injection category.");
        assertThat(mapping.getAiModel()).isEqualTo("claude-haiku-4-5-20251001"); // config default, not the request

        // 4) The audit trail records the AI origin.
        mockMvc.perform(get("/api/findings/" + f.getId()))
                .andExpect(jsonPath("$.audit[*].comment", hasItem(containsString("(AI-suggested)"))));
    }

    @Test
    @WithMockUser(username = OWNER)
    void cannotAcceptAControlThatWasNotSuggested() throws Exception {
        when(strategy.suggest(any(), any())).thenReturn(List.of(new ControlSuggestion(a03, 0.9, "x")));
        Finding f = seed("CM-AC-2");
        mockMvc.perform(post("/api/findings/" + f.getId() + "/suggest-controls").with(csrf()))
                .andExpect(status().isOk());

        // A different control the server never suggested → rejected (can't fabricate provenance).
        Long otherControlId = controls.findAll().stream()
                .map(Control::getId).filter(id -> !id.equals(a03.getId())).findFirst().orElseThrow();
        mockMvc.perform(post("/api/findings/" + f.getId() + "/accept-suggestion").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(otherControlId)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = OWNER)
    void manualMappingCannotForgeAiProvenance() throws Exception {
        Finding f = seed("CM-AC-3");
        // A client tries to smuggle AI provenance through the MANUAL endpoint — the extra fields are ignored.
        mockMvc.perform(post("/api/findings/" + f.getId() + "/controls").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"controlId\":" + a03.getId()
                                + ",\"source\":\"AI_SUGGESTED\",\"aiConfidence\":0.99,\"aiModel\":\"forged\"}"))
                .andExpect(status().isOk());

        FindingControlMapping mapping = mappings.findByFinding_IdAndControl_Id(f.getId(), a03.getId()).orElseThrow();
        assertThat(mapping.getSource()).isEqualTo(MappingSource.MANUAL);
        assertThat(mapping.getAiConfidence()).isNull();
        assertThat(mapping.getAiModel()).isNull();
    }

    @Test
    @WithMockUser(username = OTHER)
    void nonOwnerCannotAccept() throws Exception {
        Finding f = seed("CM-AC-4"); // owned by OWNER
        mockMvc.perform(post("/api/findings/" + f.getId() + "/accept-suggestion").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(a03.getId())))
                .andExpect(status().isForbidden());
    }
}
