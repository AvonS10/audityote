package io.muzoo.ssc.controlmap.web.dto;

import io.muzoo.ssc.controlmap.domain.User;

/**
 * The signed-in user as returned by login and {@code GET /api/auth/me} (PLAN §10/§7.11): the account
 * menu uses {@code name}/{@code role}. Role travels as the canonical enum name (ANALYST/REVIEWER);
 * the frontend maps it to the display chip. Never exposes the password hash.
 */
public record UserResponse(Long id, String email, String name, String role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole().name());
    }
}
