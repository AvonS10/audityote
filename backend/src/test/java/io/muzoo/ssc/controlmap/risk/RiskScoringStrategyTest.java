package io.muzoo.ssc.controlmap.risk;

import static org.assertj.core.api.Assertions.assertThat;

import io.muzoo.ssc.controlmap.domain.Asset;
import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.domain.Role;
import io.muzoo.ssc.controlmap.domain.Severity;
import io.muzoo.ssc.controlmap.domain.User;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Strategy selection + scoring (PLAN §9): CVSS when present, else the severity-derived fallback.
 * Pure unit test — the service is built from the real strategy beans in {@code @Order} sequence.
 */
class RiskScoringStrategyTest {

    private final RiskScoringService service =
            new RiskScoringService(List.of(new CvssScoringStrategy(), new SeverityScoringStrategy()));

    private static final User OWNER = new User("o@risk.test", "Owner", "x", Role.ANALYST);

    private static Finding finding(Severity severity, String cvss) {
        return new Finding("CM-R-1", "t", "d", severity,
                cvss == null ? null : new BigDecimal(cvss), OWNER, new Asset("sys", null, null, null));
    }

    @Test
    void usesCvssWhenPresent() {
        RiskScore score = service.score(finding(Severity.HIGH, "8.2"));
        assertThat(score.value()).isEqualByComparingTo("8.2");
        assertThat(score.source()).isEqualTo(RiskSource.CVSS);
    }

    @Test
    void cvssTakesPrecedenceOverSeverity() {
        // Even with a LOW severity label, a present CVSS wins (the CVSS strategy is @Order(1)).
        RiskScore score = service.score(finding(Severity.LOW, "9.8"));
        assertThat(score.value()).isEqualByComparingTo("9.8");
        assertThat(score.source()).isEqualTo(RiskSource.CVSS);
    }

    @Test
    void derivesFromSeverityWhenNoCvss() {
        assertThat(service.score(finding(Severity.CRITICAL, null)).value()).isEqualByComparingTo("9.0");
        assertThat(service.score(finding(Severity.HIGH, null)).value()).isEqualByComparingTo("7.5");
        assertThat(service.score(finding(Severity.MEDIUM, null)).value()).isEqualByComparingTo("5.0");
        assertThat(service.score(finding(Severity.LOW, null)).value()).isEqualByComparingTo("2.0");
        assertThat(service.score(finding(Severity.HIGH, null)).source()).isEqualTo(RiskSource.SEVERITY);
    }

    @Test
    void normalisesToOneDecimal() {
        // A whole-number CVSS still presents as N.0 to match the CVSS display.
        assertThat(service.score(finding(Severity.HIGH, "7")).value().toPlainString()).isEqualTo("7.0");
    }
}
