package io.muzoo.ssc.controlmap.ai;

import io.muzoo.ssc.controlmap.domain.Control;

/**
 * One AI-suggested mapping: a catalog {@link Control} the model recommends for a finding, its
 * {@code confidence} (0.0–1.0), and a one-line {@code rationale}. The control is always a real,
 * resolved catalog entry — {@link ClaudeCatalogStrategy} drops any code the model invents (grounding),
 * so a {@code ControlSuggestion} never carries a hallucinated code. A suggestion is only a
 * recommendation; it becomes a mapping only if an analyst accepts it (S2), never automatically.
 */
public record ControlSuggestion(Control control, double confidence, String rationale) {
}
