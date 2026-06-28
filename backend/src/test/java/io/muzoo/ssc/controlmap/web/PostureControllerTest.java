package io.muzoo.ssc.controlmap.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Posture endpoint wiring + auth (#19). Asserts shape, not absolute counts (those depend on the
 * seeded/dev data): the gauge is a 0–100 int and the breakdowns/heatmap come back fully shaped.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostureControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @WithMockUser
    void returnsFullyShapedRollup() throws Exception {
        mockMvc.perform(get("/api/posture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").isNumber())
                .andExpect(jsonPath("$.deltaPts").value(0))
                .andExpect(jsonPath("$.bySeverity.length()").value(4))
                .andExpect(jsonPath("$.bySeverity[0].key").value("critical"))
                .andExpect(jsonPath("$.byStatus.length()").value(7))
                .andExpect(jsonPath("$.heatStatuses.length()").value(7))
                .andExpect(jsonPath("$.heatRows.length()").value(4))
                .andExpect(jsonPath("$.heatRows[0].cells.length()").value(7));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/posture")).andExpect(status().isUnauthorized());
    }
}
