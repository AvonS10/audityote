package io.muzoo.ssc.controlmap.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Editable account profile fields (#5). Email and role are managed by the org, not self-service. */
public record ProfileUpdateRequest(
        @NotBlank(message = "Name is required.")
        @Size(max = 120, message = "Name must be at most 120 characters.")
        String name) {
}
