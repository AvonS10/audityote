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

/**
 * Catalog API against the seeded data (the @SpringBootTest boot runs the seeder). Authenticated
 * read-only endpoints: frameworks, controls by framework, search, 404 for unknown frameworks.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void listsTheThreeFrameworks() throws Exception {
        mockMvc.perform(get("/api/frameworks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.slug=='iso27001')].name").value("ISO/IEC 27001"));
    }

    @Test
    @WithMockUser
    void listsControlsForAFramework() throws Exception {
        mockMvc.perform(get("/api/controls").param("framework", "iso27001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(93))
                .andExpect(jsonPath("$[0].framework").value("iso27001"))
                // Natural code order, insertion-independent: A.5.1, A.5.2, … (not A.5.1, A.5.10, …).
                .andExpect(jsonPath("$[0].code").value("A.5.1"))
                .andExpect(jsonPath("$[1].code").value("A.5.2"))
                .andExpect(jsonPath("$[?(@.code=='A.8.28')].category").value("Technological"));
    }

    @Test
    @WithMockUser
    void searchFiltersControls() throws Exception {
        mockMvc.perform(get("/api/controls").param("framework", "owasp").param("q", "injection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("A03"));
    }

    @Test
    @WithMockUser
    void unknownFrameworkReturns404() throws Exception {
        mockMvc.perform(get("/api/controls").param("framework", "bogus"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/frameworks")).andExpect(status().isUnauthorized());
    }
}
