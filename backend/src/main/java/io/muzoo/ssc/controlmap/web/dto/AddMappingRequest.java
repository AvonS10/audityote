package io.muzoo.ssc.controlmap.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Map a control to a finding (PLAN §10). {@code source} defaults to MANUAL; the {@code ai*} fields
 * carry provenance only when an analyst accepts an AI suggestion (stretch §4) — ignored otherwise.
 */
public record AddMappingRequest(
        @NotNull Long controlId,
        String source,
        Double aiConfidence,
        String aiRationale,
        String aiModel) {
}
