package io.muzoo.ssc.controlmap.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Create/edit a finding (PLAN §10). Bean Validation covers the simple rules; the service applies the
 * business rule that severity is derived from CVSS when present and otherwise required (§16 #6).
 * Status is never accepted from the client — it advances only through workflow actions.
 */
public record FindingRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        String severity,
        @DecimalMin("0.0") @DecimalMax("10.0")
        @Digits(integer = 2, fraction = 1, message = "CVSS must have at most one decimal place (0.0–10.0).")
        BigDecimal cvss,
        @Valid @NotNull AssetRequest asset) {
}
