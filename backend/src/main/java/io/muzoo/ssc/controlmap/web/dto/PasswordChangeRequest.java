package io.muzoo.ssc.controlmap.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Change-password payload (#5): the current password is re-verified server-side before the change. */
public record PasswordChangeRequest(
        @NotBlank(message = "Current password is required.")
        String currentPassword,

        @NotBlank(message = "New password is required.")
        @Size(min = 8, max = 200, message = "New password must be at least 8 characters.")
        String newPassword) {
}
