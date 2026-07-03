package io.muzoo.ssc.controlmap.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.muzoo.ssc.controlmap.domain.Role;
import io.muzoo.ssc.controlmap.domain.User;
import io.muzoo.ssc.controlmap.domain.UserAuditLog;
import io.muzoo.ssc.controlmap.repository.UserAuditLogRepository;
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
 * Admin user-management (#admin): ADMIN-only list + role/active/password mutations, self-lockout guards,
 * audit writes, and force-logout of a deactivated user's session.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserAdminTest {

    private static final String ADMIN = "admin@ua.test";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private UserAuditLogRepository userAudit;
    @Autowired private PasswordEncoder encoder;

    private User admin;
    private User analyst;

    @BeforeEach
    void setUp() {
        admin = users.save(new User(ADMIN, "Admin", encoder.encode("adminpass1"), Role.ADMIN));
        analyst = users.save(new User("analyst@ua.test", "Analyst", encoder.encode("analystpw1"), Role.ANALYST));
    }

    private void doPut(String path, String body, int expected) throws Exception {
        mockMvc.perform(put(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected));
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void listsUsers() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].email", org.hamcrest.Matchers.hasItem("analyst@ua.test")));
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void promotesAnalystToReviewerAndAudits() throws Exception {
        mockMvc.perform(put("/api/users/" + analyst.getId() + "/role").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"REVIEWER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("REVIEWER"));
        assertThat(users.findById(analyst.getId()).orElseThrow().getRole()).isEqualTo(Role.REVIEWER);
        assertThat(userAudit.findAll()).extracting(UserAuditLog::getAction).contains("role-changed");
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void cannotChangeOwnRole() throws Exception {
        doPut("/api/users/" + admin.getId() + "/role", "{\"role\":\"ANALYST\"}", 403);
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void rejectsInvalidRole() throws Exception {
        doPut("/api/users/" + analyst.getId() + "/role", "{\"role\":\"SUPERUSER\"}", 400);
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void deactivatesUser() throws Exception {
        doPut("/api/users/" + analyst.getId() + "/active", "{\"active\":false}", 200);
        assertThat(users.findById(analyst.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void cannotDeactivateSelf() throws Exception {
        doPut("/api/users/" + admin.getId() + "/active", "{\"active\":false}", 403);
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void resetsPassword() throws Exception {
        doPut("/api/users/" + analyst.getId() + "/password", "{\"newPassword\":\"freshpass1\"}", 204);
        assertThat(encoder.matches("freshpass1", users.findById(analyst.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void nonAdminIsForbidden() throws Exception {
        mockMvc.perform(get("/api/users")).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "deactivated@ua.test", roles = "ANALYST")
    void deactivatedUserIsForcedOut() throws Exception {
        User gone = users.save(new User("deactivated@ua.test", "Gone", encoder.encode("xpassword1"), Role.ANALYST));
        gone.setActive(false);
        users.save(gone);
        // The ActiveUserFilter re-checks active on every request → the still-"authenticated" call is 401.
        mockMvc.perform(get("/api/findings")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "demoted@ua.test", roles = "ADMIN")
    void staleRoleAuthorityIsForcedOut() throws Exception {
        // Session was granted ROLE_ADMIN at login, but the account has since been demoted to ANALYST.
        // The ActiveUserFilter re-checks the DB role each request → the stale-admin call is 401, so a
        // demoted admin cannot keep exercising admin power on their live session (must re-authenticate).
        users.save(new User("demoted@ua.test", "Demoted", encoder.encode("xpassword1"), Role.ANALYST));
        mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }
}
