package io.muzoo.ssc.controlmap.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.muzoo.ssc.controlmap.ai.ControlSuggestion;
import io.muzoo.ssc.controlmap.ai.MappingSuggestionStrategy;
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
 * The suggest-controls endpoint with the feature ENABLED — a mocked {@link MappingSuggestionStrategy}
 * bean stands in for Claude, so there is no live call and no spend. Its presence <b>is</b> the "enabled"
 * signal (the service checks for the bean). Covers the happy path + JSON shape, owner/auth gating, the
 * non-editable guard, and that a cached second call does not re-invoke the model.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SuggestControlsTest {

    private static final String OWNER = "owner@suggest.test";
    private static final String OTHER = "other@suggest.test";

    @MockitoBean
    private MappingSuggestionStrategy strategy;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private FindingRepository findings;
    @Autowired private FrameworkRepository frameworks;
    @Autowired private ControlRepository controls;

    private User owner;
    private Control a03;

    @BeforeEach
    void setUp() {
        owner = users.save(new User(OWNER, "Owner", "x", Role.ANALYST));
        users.save(new User(OTHER, "Other", "x", Role.ANALYST));
        Long owaspId = frameworks.findBySlug("owasp").orElseThrow().getId();
        a03 = controls.findByFramework_IdAndCode(owaspId, "A03").orElseThrow();
    }

    private Finding seed(String reference, FindingStatus statusValue) {
        Finding f = findings.save(new Finding(reference, "SQL injection", "concatenated query",
                Severity.HIGH, new BigDecimal("7.5"), owner, new Asset("web", null, null, null)));
        f.setStatus(statusValue);
        return findings.save(f);
    }

    @Test
    @WithMockUser(username = OWNER)
    void returnsGroundedSuggestions() throws Exception {
        when(strategy.suggest(any(), any())).thenReturn(List.of(new ControlSuggestion(a03, 0.91, "Injection.")));
        Finding f = seed("CM-SG-1", FindingStatus.OPEN);

        mockMvc.perform(post("/api/findings/" + f.getId() + "/suggest-controls").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].control.code").value("A03"))
                .andExpect(jsonPath("$[0].control.framework").value("owasp"))
                .andExpect(jsonPath("$[0].confidence").value(0.91))
                .andExpect(jsonPath("$[0].rationale").value("Injection."));
    }

    @Test
    @WithMockUser(username = OTHER)
    void nonOwnerIsForbidden() throws Exception {
        Finding f = seed("CM-SG-2", FindingStatus.OPEN);
        mockMvc.perform(post("/api/findings/" + f.getId() + "/suggest-controls").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedIsUnauthorized() throws Exception {
        Finding f = seed("CM-SG-3", FindingStatus.OPEN);
        mockMvc.perform(post("/api/findings/" + f.getId() + "/suggest-controls").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = OWNER)
    void nonEditableFindingIsConflict() throws Exception {
        Finding f = seed("CM-SG-4", FindingStatus.SUBMITTED);
        mockMvc.perform(post("/api/findings/" + f.getId() + "/suggest-controls").with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = OWNER)
    void secondCallForSameFindingIsCachedAndDoesNotRecallTheModel() throws Exception {
        when(strategy.suggest(any(), any())).thenReturn(List.of(new ControlSuggestion(a03, 0.8, "x")));
        Finding f = seed("CM-SG-5", FindingStatus.OPEN);
        String url = "/api/findings/" + f.getId() + "/suggest-controls";

        mockMvc.perform(post(url).with(csrf())).andExpect(status().isOk());
        mockMvc.perform(post(url).with(csrf())).andExpect(status().isOk());

        verify(strategy, times(1)).suggest(any(), any()); // the second call was served from cache
    }

    @Test
    @WithMockUser(username = OWNER)
    void editingTheFindingInvalidatesTheCacheSoTheNextSuggestRecallsTheModel() throws Exception {
        when(strategy.suggest(any(), any())).thenReturn(List.of(new ControlSuggestion(a03, 0.8, "x")));
        Finding f = seed("CM-SG-6", FindingStatus.OPEN);
        String suggestUrl = "/api/findings/" + f.getId() + "/suggest-controls";

        mockMvc.perform(post(suggestUrl).with(csrf())).andExpect(status().isOk());

        // Edit the finding through the real write path: this publishes a FindingAuditEvent, which the
        // SuggestionCacheInvalidator observes and uses to drop the now-stale cached suggestions.
        mockMvc.perform(put("/api/findings/" + f.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"SQL injection (revised)\",\"description\":\"updated detail\","
                                + "\"severity\":\"high\",\"cvss\":null,\"asset\":{\"name\":\"web\"}}"))
                .andExpect(status().isOk());

        // Cache was invalidated by the edit, so this is a fresh, uncached call → the model is asked again.
        mockMvc.perform(post(suggestUrl).with(csrf())).andExpect(status().isOk());

        verify(strategy, times(2)).suggest(any(), any());
    }
}
