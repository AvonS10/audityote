package io.muzoo.ssc.controlmap.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Self-service registration payload (#reg). Domain is validated server-side against the allowlist. */
public record RegisterRequest(
        @NotBlank(message = "Name is required.")
        @Size(max = 120, message = "Name must be at most 120 characters.")
        String name,

        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 200)
        String email,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 200, message = "Password must be at least 8 characters.")
        String password) {
}
