package io.muzoo.ssc.controlmap.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.muzoo.ssc.controlmap.ai.AiSuggestionProperties;
import io.muzoo.ssc.controlmap.domain.Asset;
import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.domain.FindingControlMapping;
import io.muzoo.ssc.controlmap.domain.MappingSource;
import io.muzoo.ssc.controlmap.domain.Role;
import io.muzoo.ssc.controlmap.domain.Severity;
import io.muzoo.ssc.controlmap.domain.User;
import io.muzoo.ssc.controlmap.repository.FindingControlMappingRepository;
import io.muzoo.ssc.controlmap.repository.FindingRepository;
import io.muzoo.ssc.controlmap.repository.UserRepository;
import io.muzoo.ssc.controlmap.web.dto.SuggestionResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A <b>manual, live</b> end-to-end smoke test (PLAN §12): with the feature enabled and a real
 * {@code ANTHROPIC_API_KEY}, it drives the actual HTTP endpoints — suggest-controls (a real Claude call)
 * then accept-suggestion — and verifies a persisted {@code AI_SUGGESTED} mapping with server-authoritative
 * provenance. Skipped unless {@code -Dai.live=true}, so CI never runs it. Run:
 * {@code mvn test -Dtest=AiLiveEndToEndTest -Dai.live=true -Dsurefire.useFile=false} with {@code .env} sourced.
 */
@SpringBootTest(properties = "controlmap.ai.enabled=true")
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "ai.live", matches = "true")
@Transactional
class AiLiveEndToEndTest {

    private static final String OWNER = "live-e2e@smoke.test";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private FindingRepository findings;
    @Autowired private FindingControlMappingRepository mappings;
    @Autowired private AiSuggestionProperties properties;

    @Test
    @WithMockUser(username = OWNER)
    void suggestThenAcceptPersistsServerAuthoritativeProvenance() throws Exception {
        User owner = users.save(new User(OWNER, "Live E2E", "x", Role.ANALYST));
        Finding f = findings.save(new Finding("CM-LIVE-E2E-1", "SQL injection in login form",
                "User-supplied input is concatenated into a SQL query in the authentication endpoint, "
                        + "allowing authentication bypass and data exfiltration.",
                Severity.HIGH, null, owner,
                new Asset("customer-portal", "prod", "auth-service", "https://portal.example/login")));

        // 1) Real Claude call through the endpoint.
        String suggestBody = mockMvc.perform(post("/api/findings/" + f.getId() + "/suggest-controls").with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<SuggestionResponse> suggestions = json.readValue(suggestBody, new TypeReference<>() {});
        assertThat(suggestions).as("live Claude call should return grounded suggestions").isNotEmpty();

        SuggestionResponse top = suggestions.get(0);
        System.out.println("\n=== LIVE E2E: Claude suggested " + suggestions.size() + " control(s); accepting "
                + top.control().code() + " — " + top.control().title() + " (conf=" + top.confidence() + ") ===");

        // 2) Accept the top suggestion — client sends only the controlId.
        mockMvc.perform(post("/api/findings/" + f.getId() + "/accept-suggestion").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"controlId\":" + top.control().id() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.controls[*].code", hasItem(top.control().code())));

        // 3) The persisted mapping carries real, server-authoritative AI provenance.
        FindingControlMapping mapping =
                mappings.findByFinding_IdAndControl_Id(f.getId(), top.control().id()).orElseThrow();
        assertThat(mapping.getSource()).isEqualTo(MappingSource.AI_SUGGESTED);
        assertThat(mapping.getAiModel()).isEqualTo(properties.getModel());
        assertThat(mapping.getAiConfidence()).isEqualTo(top.confidence());
        assertThat(mapping.getAiRationale()).isEqualTo(top.rationale());
        System.out.println("=== LIVE E2E: persisted AI_SUGGESTED mapping — model=" + mapping.getAiModel()
                + ", conf=" + mapping.getAiConfidence() + ", rationale=\"" + mapping.getAiRationale() + "\" ===\n");
    }
}
