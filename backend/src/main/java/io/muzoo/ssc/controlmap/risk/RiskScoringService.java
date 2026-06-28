package io.muzoo.ssc.controlmap.risk;

import io.muzoo.ssc.controlmap.domain.Finding;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The Strategy context (PLAN §9/§11): holds the ordered {@link RiskScoringStrategy} beans and selects
 * the first that applies to a finding — CVSS when present, else the severity-derived fallback. Spring
 * injects the list in {@code @Order} sequence, so the default policy is just "first applicable wins".
 * The score is normalised to one decimal place to match the CVSS presentation.
 */
@Service
public class RiskScoringService {

    private final List<RiskScoringStrategy> strategies;

    public RiskScoringService(List<RiskScoringStrategy> strategies) {
        this.strategies = strategies;
    }

    /** Computes the effective risk score for a finding via the first applicable strategy. */
    public RiskScore score(Finding finding) {
        RiskScoringStrategy strategy = strategies.stream()
                .filter(s -> s.appliesTo(finding))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No risk scoring strategy applies to finding " + finding.getId()));
        return new RiskScore(strategy.score(finding).setScale(1, RoundingMode.HALF_UP), strategy.source());
    }
}
