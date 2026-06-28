package io.muzoo.ssc.controlmap.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin sets a temporary password for a user (#admin); the user can then change it in Account settings. */
public record AdminPasswordResetRequest(
        @NotBlank(message = "New password is required.")
        @Size(min = 8, max = 200, message = "Password must be at least 8 characters.")
        String newPassword) {
}
