package io.muzoo.ssc.controlmap.web.dto;

import io.muzoo.ssc.controlmap.domain.User;

/**
 * The signed-in user as returned by login and {@code GET /api/auth/me} (PLAN §10/§7.11): the account
 * menu uses {@code name}/{@code role}. Role travels as the canonical enum name (ANALYST/REVIEWER);
 * the frontend maps it to the display chip. {@code demo} is true for a locked public demo account, so
 * the account screen can disable self-service edits (the server-side 403 is the actual control).
 * Never exposes the password hash.
 */
public record UserResponse(Long id, String email, String name, String role, boolean demo) {

    public static UserResponse from(User user, boolean demo) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole().name(), demo);
    }
}
