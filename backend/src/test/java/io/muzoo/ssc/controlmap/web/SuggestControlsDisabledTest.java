package io.muzoo.ssc.controlmap.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.muzoo.ssc.controlmap.domain.Asset;
import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.domain.Role;
import io.muzoo.ssc.controlmap.domain.Severity;
import io.muzoo.ssc.controlmap.domain.User;
import io.muzoo.ssc.controlmap.repository.FindingRepository;
import io.muzoo.ssc.controlmap.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The suggest-controls endpoint with the feature OFF (the default — no {@code MappingSuggestionStrategy}
 * bean, since {@code AiConfig} only builds it when {@code controlmap.ai.enabled=true}). Even for a valid,
 * owned, editable finding it returns 503, so the SPA falls back to manual mapping (PLAN §7.12).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SuggestControlsDisabledTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private FindingRepository findings;

    @Test
    @WithMockUser(username = "owner@disabled.test")
    void returns503WhenFeatureIsOff() throws Exception {
        User owner = users.save(new User("owner@disabled.test", "Owner", "x", Role.ANALYST));
        Finding f = findings.save(new Finding("CM-SG-OFF", "t", "d",
                Severity.HIGH, null, owner, new Asset("s", null, null, null)));

        mockMvc.perform(post("/api/findings/" + f.getId() + "/suggest-controls").with(csrf()))
                .andExpect(status().isServiceUnavailable());
    }
}
