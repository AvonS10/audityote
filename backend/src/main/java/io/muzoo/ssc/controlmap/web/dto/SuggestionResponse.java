package io.muzoo.ssc.controlmap.web.dto;

/**
 * One AI-suggested mapping returned by {@code POST /api/findings/{id}/suggest-controls} (PLAN §10):
 * the candidate {@link ControlResponse control}, the model's {@code confidence} (0.0–1.0), and a
 * one-line {@code rationale}. Only real catalog controls appear here — grounding (S1) drops any code
 * the model invents. A suggestion is a recommendation only; accepting one creates a mapping (S2b),
 * never automatically, and it still funnels through the normal review workflow.
 */
public record SuggestionResponse(ControlResponse control, double confidence, String rationale) {
}
