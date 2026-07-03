package io.muzoo.ssc.controlmap.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Accept an AI-suggested control for a finding (S2b): the client sends only the {@code controlId} it
 * wants to accept — an <i>intent</i>. It deliberately carries <b>no</b> provenance fields: the server
 * stamps confidence/rationale/model itself, from the cached suggestion it actually produced, so the
 * audit trail can't be spoofed into claiming a hand-picked control was AI-suggested (PLAN §3/§4).
 */
public record AcceptSuggestionRequest(@NotNull Long controlId) {
}
