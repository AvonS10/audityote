package io.muzoo.ssc.controlmap.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.muzoo.ssc.controlmap.domain.Role;
import io.muzoo.ssc.controlmap.domain.User;
import io.muzoo.ssc.controlmap.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service registration (#reg): gated by the email-domain allowlist, new accounts are ANALYST, and
 * duplicate emails / bad input are rejected. The allowlist is set for this context via test properties.
 */
@SpringBootTest(properties = "controlmap.signup.allowed-domains=corp.test, partner.test")
@AutoConfigureMockMvc
@Transactional
class RegistrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;

    private ResultActions register(String body) throws Exception {
        return mockMvc.perform(post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void registersAllowedDomainAsAnalyst() throws Exception {
        register("{\"name\":\"New Hire\",\"email\":\"new.hire@corp.test\",\"password\":\"secret123\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new.hire@corp.test"))
                .andExpect(jsonPath("$.role").value("ANALYST"));
        assertThat(users.findByEmail("new.hire@corp.test").orElseThrow().getRole()).isEqualTo(Role.ANALYST);
    }

    @Test
    void normalisesEmailToLowercase() throws Exception {
        register("{\"name\":\"Caps\",\"email\":\"Mixed.Case@Partner.test\",\"password\":\"secret123\"}")
                .andExpect(status().isCreated());
        assertThat(users.findByEmail("mixed.case@partner.test")).isPresent();
    }

    @Test
    void rejectsDisallowedDomain() throws Exception {
        register("{\"name\":\"Outsider\",\"email\":\"x@evil.test\",\"password\":\"secret123\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsDuplicateEmail() throws Exception {
        users.save(new User("dup@corp.test", "Existing", encoder.encode("whatever1"), Role.ANALYST));
        register("{\"name\":\"Dup\",\"email\":\"dup@corp.test\",\"password\":\"secret123\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidEmail() throws Exception {
        register("{\"name\":\"Bad\",\"email\":\"not-an-email\",\"password\":\"secret123\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsShortPassword() throws Exception {
        register("{\"name\":\"Short\",\"email\":\"short@corp.test\",\"password\":\"abc\"}")
                .andExpect(status().isBadRequest());
    }
}
