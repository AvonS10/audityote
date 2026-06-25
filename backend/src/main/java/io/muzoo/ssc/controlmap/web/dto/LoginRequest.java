package io.muzoo.ssc.controlmap.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Login credentials. Validated before authentication; failures return the 400 error model. */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password) {
}
