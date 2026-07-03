package io.muzoo.ssc.controlmap.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Manually map a control to a finding (PLAN §10) — just the {@code controlId}. This path always creates
 * a {@code MANUAL} mapping and <b>cannot</b> set AI provenance: that has exactly one door, the
 * accept-suggestion flow ({@link AcceptSuggestionRequest}), which stamps provenance server-side. Keeping
 * the manual request unable to express AI origin is what prevents the audit trail from being spoofed.
 */
public record AddMappingRequest(@NotNull Long controlId) {
}
