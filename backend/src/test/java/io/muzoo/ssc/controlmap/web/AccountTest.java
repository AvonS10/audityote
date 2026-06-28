package io.muzoo.ssc.controlmap.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.muzoo.ssc.controlmap.domain.Role;
import io.muzoo.ssc.controlmap.domain.User;
import io.muzoo.ssc.controlmap.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account settings (#5): a user edits their own display name and changes their password — the current
 * password is re-verified server-side, and the new one must be valid and different.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountTest {

    private static final String EMAIL = "acct@notif.test";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        users.save(new User(EMAIL, "Original Name", encoder.encode("oldpass123"), Role.ANALYST));
    }

    private void doPut(String path, String body, int expected) throws Exception {
        mockMvc.perform(put(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updatesDisplayName() throws Exception {
        mockMvc.perform(put("/api/account/profile").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.email").value(EMAIL));
        assertThat(users.findByEmail(EMAIL).orElseThrow().getName()).isEqualTo("New Name");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void blankNameIsRejected() throws Exception {
        doPut("/api/account/profile", "{\"name\":\"  \"}", 400);
    }

    @Test
    @WithMockUser(username = EMAIL)
    void changesPasswordWithCorrectCurrent() throws Exception {
        doPut("/api/account/password", "{\"currentPassword\":\"oldpass123\",\"newPassword\":\"newpass456\"}", 204);
        assertThat(encoder.matches("newpass456", users.findByEmail(EMAIL).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void wrongCurrentPasswordIsRejected() throws Exception {
        doPut("/api/account/password", "{\"currentPassword\":\"nope\",\"newPassword\":\"newpass456\"}", 400);
    }

    @Test
    @WithMockUser(username = EMAIL)
    void sameAsCurrentPasswordIsRejected() throws Exception {
        doPut("/api/account/password", "{\"currentPassword\":\"oldpass123\",\"newPassword\":\"oldpass123\"}", 400);
    }

    @Test
    @WithMockUser(username = EMAIL)
    void tooShortNewPasswordIsRejected() throws Exception {
        doPut("/api/account/password", "{\"currentPassword\":\"oldpass123\",\"newPassword\":\"short\"}", 400);
    }

    @Test
    void requiresAuthentication() throws Exception {
        doPut("/api/account/profile", "{\"name\":\"X\"}", 401);
    }
}
