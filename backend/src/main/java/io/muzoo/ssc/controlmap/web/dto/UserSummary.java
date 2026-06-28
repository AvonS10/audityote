package io.muzoo.ssc.controlmap.web.dto;

import io.muzoo.ssc.controlmap.domain.User;

/** A user as shown in the admin Users list (#admin). Role travels as the canonical enum name. */
public record UserSummary(Long id, String name, String email, String role, boolean active) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getName(), user.getEmail(), user.getRole().name(), user.isActive());
    }
}
