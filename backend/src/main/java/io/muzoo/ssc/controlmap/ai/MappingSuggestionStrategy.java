package io.muzoo.ssc.controlmap.ai;

import io.muzoo.ssc.controlmap.domain.Control;
import io.muzoo.ssc.controlmap.domain.Finding;
import java.util.List;

/**
 * Strategy pattern (PLAN §11) for the AI control-mapping stretch: one interchangeable way to suggest
 * controls for a finding. {@link ClaudeCatalogStrategy} is the default (Claude, grounded to the seeded
 * catalog); a future {@code RagStrategy} (S4, pgvector retrieval) drops in as another implementation
 * behind this interface without touching callers — Open/Closed. The suggestions are grounded to the
 * supplied {@code catalog}: every returned {@link ControlSuggestion} references a real catalog control.
 *
 * <p>Suggestions never bypass the workflow (PLAN §8): they are recommendations only, applied solely
 * when an analyst accepts one (S2), and a reviewer still signs the finding off.
 */
public interface MappingSuggestionStrategy {

    /**
     * Suggests controls from {@code catalog} that mitigate {@code finding}, best-confidence first.
     *
     * @param finding the finding to map
     * @param catalog the controls the suggestions must be grounded against (codes outside it are dropped)
     * @return grounded suggestions (possibly empty); never contains an invented/hallucinated control
     * @throws MappingSuggestionException if the model output cannot be parsed or the upstream call fails
     */
    List<ControlSuggestion> suggest(Finding finding, List<Control> catalog);
}
