package io.muzoo.ssc.controlmap.risk;

import io.muzoo.ssc.controlmap.domain.Finding;
import java.math.BigDecimal;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Preferred strategy (PLAN §9): when a finding carries a CVSS base score, that score is the risk
 * score directly. {@code @Order(1)} so {@link RiskScoringService} consults it before the fallback.
 */
@Component
@Order(1)
public class CvssScoringStrategy implements RiskScoringStrategy {

    @Override
    public boolean appliesTo(Finding finding) {
        return finding.getCvssScore() != null;
    }

    @Override
    public BigDecimal score(Finding finding) {
        return finding.getCvssScore();
    }

    @Override
    public RiskSource source() {
        return RiskSource.CVSS;
    }
}
