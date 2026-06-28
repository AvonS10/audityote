package io.muzoo.ssc.controlmap.risk;

import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.domain.Severity;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Fallback strategy (PLAN §9): when a finding has no CVSS score, map its severity band to a
 * representative score (tunable). {@code appliesTo} is always true and {@code @Order(2)} places it
 * last, so it catches every finding the CVSS strategy doesn't.
 */
@Component
@Order(2)
public class SeverityScoringStrategy implements RiskScoringStrategy {

    /** Representative 0–10 score per severity band (PLAN §9 defaults; tunable). */
    private static final Map<Severity, BigDecimal> SCORES = Map.of(
            Severity.CRITICAL, new BigDecimal("9.0"),
            Severity.HIGH, new BigDecimal("7.5"),
            Severity.MEDIUM, new BigDecimal("5.0"),
            Severity.LOW, new BigDecimal("2.0"));

    @Override
    public boolean appliesTo(Finding finding) {
        return true;
    }

    @Override
    public BigDecimal score(Finding finding) {
        BigDecimal mapped = SCORES.get(finding.getSeverity());
        // Severity is non-null on every finding; guard defensively rather than risk an NPE.
        return mapped != null ? mapped : BigDecimal.ZERO;
    }

    @Override
    public RiskSource source() {
        return RiskSource.SEVERITY;
    }
}
