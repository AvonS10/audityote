package io.muzoo.ssc.controlmap.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Admin sets a user's role (#admin). Validated against the Role enum server-side. */
public record RoleChangeRequest(@NotBlank(message = "Role is required.") String role) {
}
