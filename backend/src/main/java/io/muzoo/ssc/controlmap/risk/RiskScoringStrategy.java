package io.muzoo.ssc.controlmap.risk;

import io.muzoo.ssc.controlmap.domain.Finding;
import java.math.BigDecimal;

/**
 * Strategy pattern (PLAN §9/§11): one interchangeable algorithm for scoring a finding's risk on the
 * 0.0–10.0 scale. {@link #appliesTo} lets {@link RiskScoringService} pick the right strategy at
 * runtime (CVSS when present, else the severity-derived fallback); adding a new algorithm (e.g. a
 * likelihood×impact strategy) is a new bean, not an edit here — Open/Closed.
 */
public interface RiskScoringStrategy {

    /** Whether this strategy can score the given finding (e.g. the CVSS strategy needs a CVSS value). */
    boolean appliesTo(Finding finding);

    /** The finding's risk score on the 0.0–10.0 scale; only called when {@link #appliesTo} is true. */
    BigDecimal score(Finding finding);

    /** Identifies which algorithm this is, so the API can mark a derived (non-CVSS) score. */
    RiskSource source();
}
