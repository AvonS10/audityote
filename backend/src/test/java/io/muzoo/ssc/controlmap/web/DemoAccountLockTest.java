package io.muzoo.ssc.controlmap.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Published public demo accounts (listed in {@code controlmap.demo.locked-emails}) are blocked
 * server-side from self-service password/profile changes, so a visitor using the shared demo login
 * cannot lock everyone else out. Enforcement is a 403 in {@link AccountService}, not a hidden UI
 * control. A normal account is unaffected, and {@code /api/auth/me} tags the locked one so the UI can
 * disable the form.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "controlmap.demo.locked-emails=demo@lock.test, other@lock.test")
class DemoAccountLockTest {

    private static final String DEMO = "demo@lock.test";
    private static final String NORMAL = "normal@lock.test";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        users.save(new User(DEMO, "Demo User", encoder.encode("oldpass123"), Role.ANALYST));
        users.save(new User(NORMAL, "Normal User", encoder.encode("oldpass123"), Role.ANALYST));
    }

    private void doPut(String path, String actingEmail, String body, int expected) throws Exception {
        mockMvc.perform(put(path).with(csrf()).with(user(actingEmail))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected));
    }

    @Test
    void lockedDemoAccountCannotChangePassword() throws Exception {
        doPut("/api/account/password", DEMO, "{\"currentPassword\":\"oldpass123\",\"newPassword\":\"newpass456\"}", 403);
        // Password is untouched — the guard runs before any mutation.
        assertThat(encoder.matches("oldpass123", users.findByEmail(DEMO).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void lockedDemoAccountCannotChangeName() throws Exception {
        doPut("/api/account/profile", DEMO, "{\"name\":\"Defaced\"}", 403);
        assertThat(users.findByEmail(DEMO).orElseThrow().getName()).isEqualTo("Demo User");
    }

    @Test
    void normalAccountCanStillChangePassword() throws Exception {
        doPut("/api/account/password", NORMAL, "{\"currentPassword\":\"oldpass123\",\"newPassword\":\"newpass456\"}", 204);
        assertThat(encoder.matches("newpass456", users.findByEmail(NORMAL).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void normalAccountCanStillChangeName() throws Exception {
        doPut("/api/account/profile", NORMAL, "{\"name\":\"Renamed\"}", 200);
        assertThat(users.findByEmail(NORMAL).orElseThrow().getName()).isEqualTo("Renamed");
    }

    @Test
    @WithMockUser(username = DEMO)
    void meFlagsLockedDemoAccount() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(true));
    }

    @Test
    @WithMockUser(username = NORMAL)
    void meDoesNotFlagNormalAccount() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(false));
    }
}
