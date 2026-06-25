package io.muzoo.ssc.controlmap.health;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller slice test for {@link HealthController}. Mocks the repository so it needs no real
 * database — this is what keeps CI green before a Postgres service exists in the pipeline.
 */
@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DatabaseHealthRepository databaseHealth;

    @Test
    void returns200AndUpWhenDatabaseReachable() throws Exception {
        given(databaseHealth.isReachable()).willReturn(true);

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("up"))
                .andExpect(jsonPath("$.db").value("up"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void returns503AndDownWhenDatabaseUnreachable() throws Exception {
        given(databaseHealth.isReachable()).willReturn(false);

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("down"))
                .andExpect(jsonPath("$.db").value("down"));
    }
}
