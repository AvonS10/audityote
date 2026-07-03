package io.muzoo.ssc.controlmap.web.dto;

/**
 * Client-visible server capabilities (PLAN §7.12). {@code aiSuggestionsEnabled} lets the SPA show the
 * "Suggest controls" affordance only when the backend can actually serve suggestions — no point offering
 * a button that would only ever return 503. Reveals no secrets, just the on/off flag.
 */
public record ConfigResponse(boolean aiSuggestionsEnabled) {
}
