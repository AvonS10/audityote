package io.muzoo.ssc.controlmap.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.muzoo.ssc.controlmap.web.dto.FindingSummary;
import io.muzoo.ssc.controlmap.web.dto.PostureResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The rule-based synthesis layer (#posture-report): each insight is a pure function of the data, so it
 * is asserted directly — given these numbers, exactly these statements (deterministic, reproducible).
 */
class PostureInsightsTest {

    private static final Instant NOW = Instant.parse("2026-07-03T00:00:00Z");

    private static FindingSummary finding(String ref, String severity, String status, String risk, Instant created) {
        return new FindingSummary(1L, ref, "t", null, severity, null,
                risk == null ? null : new BigDecimal(risk), "derived", status, List.of(), "o", created, created);
    }

    private static PostureResponse posture(long total, long active, long criticalActive,
                                           List<PostureResponse.StatusCount> byStatus) {
        return new PostureResponse(50, 0, total, active, criticalActive, 0, List.of(), byStatus, List.of(), List.of());
    }

    private static PostureResponse.StatusCount status(String key, long count) {
        return new PostureResponse.StatusCount(key, key, count);
    }

    @Test
    void namesTheLargestPostureDriverNotTheFirstNonZero() {
        // 1 critical = 10 pts vs 5 highs = 30 pts → HIGH drives the posture, not critical.
        List<FindingSummary> all = List.of(
                finding("R1", "critical", "open", "9.0", NOW),
                finding("R2", "high", "open", "7.5", NOW),
                finding("R3", "high", "open", "7.5", NOW),
                finding("R4", "high", "open", "7.5", NOW),
                finding("R5", "high", "open", "7.5", NOW),
                finding("R6", "high", "open", "7.5", NOW));
        PostureInsights.Result result = PostureInsights.compose(
                posture(6, 6, 1, List.of()), all, List.of(), NOW);
        assertThat(result.bullets()).anySatisfy(b ->
                assertThat(b).contains("high findings carry the largest share").contains("30 of 40"));
    }

    @Test
    void reportsZeroLoadWhenNothingActive() {
        List<FindingSummary> all = List.of(finding("R1", "critical", "remediated", "9.0", NOW));
        PostureInsights.Result result = PostureInsights.compose(
                posture(1, 0, 0, List.of()), all, List.of(), NOW);
        assertThat(result.bullets()).anySatisfy(b -> assertThat(b).contains("No active findings"));
    }

    @Test
    void flagsConcentrationWhenTopThreeDominate() {
        List<FindingSummary> all = List.of(
                finding("R1", "critical", "open", "9.8", NOW),
                finding("R2", "critical", "open", "9.5", NOW),
                finding("R3", "high", "open", "8.0", NOW),
                finding("R4", "low", "open", "1.0", NOW),
                finding("R5", "low", "open", "1.0", NOW));
        PostureInsights.Result result = PostureInsights.compose(
                posture(5, 5, 2, List.of()), all, List.of(), NOW);
        assertThat(result.bullets()).anySatisfy(b -> assertThat(b).contains("Risk is concentrated"));
    }

    @Test
    void agesTheOldestActiveHighOrCritical() {
        Instant old = NOW.minus(41, ChronoUnit.DAYS);
        List<FindingSummary> all = List.of(
                finding("CM-OLD", "high", "open", "7.5", old),
                finding("CM-NEW", "critical", "open", "9.0", NOW),
                finding("CM-LOWOLD", "low", "open", "2.0", NOW.minus(300, ChronoUnit.DAYS)));  // low: ignored
        PostureInsights.Result result = PostureInsights.compose(
                posture(3, 3, 1, List.of()), all, List.of(), NOW);
        assertThat(result.bullets()).anySatisfy(b ->
                assertThat(b).contains("CM-OLD").contains("41 days"));
    }

    @Test
    void countsAcceptedExposureAndRecommendsRevalidation() {
        List<FindingSummary> all = List.of(
                finding("R1", "critical", "accepted", "9.0", NOW),
                finding("R2", "low", "accepted", "2.0", NOW));
        PostureInsights.Result result = PostureInsights.compose(
                posture(2, 0, 0, List.of()), all, List.of(), NOW);
        assertThat(result.bullets()).anySatisfy(b ->
                assertThat(b).contains("2 risks formally accepted").contains("including 1 high/critical"));
        assertThat(result.recommendations()).anySatisfy(r ->
                assertThat(r).contains("Re-validate the 1 accepted high/critical risk"));
    }

    @Test
    void coverageBulletsNameWeakestFrameworkWithHonestFraming() {
        List<PostureInsights.CoverageStat> coverage = List.of(
                new PostureInsights.CoverageStat("ISO/IEC 27001 2022", 10, 9, 2),
                new PostureInsights.CoverageStat("OWASP Top 10 2025", 10, 3, 0));
        PostureInsights.Result result = PostureInsights.compose(
                posture(0, 0, 0, List.of()), List.of(), coverage, NOW);
        assertThat(result.bullets()).anySatisfy(b ->
                assertThat(b).contains("OWASP Top 10 2025 has the lowest control coverage")
                        .contains("unaffected or not yet assessed"));
        assertThat(result.bullets()).anySatisfy(b -> assertThat(b).contains("2 controls across all frameworks"));
    }

    @Test
    void recommendationsFollowPriorityOrderAndSkipInapplicable() {
        List<FindingSummary> all = List.of(finding("R1", "critical", "open", "9.8", NOW));
        PostureInsights.Result result = PostureInsights.compose(
                posture(1, 1, 1, List.of(status("submitted", 2))), all, List.of(), NOW);
        assertThat(result.recommendations()).hasSize(2);
        assertThat(result.recommendations().get(0)).contains("Remediate the 1 active critical finding");
        assertThat(result.recommendations().get(1)).contains("Clear the review queue: 2 submitted");
    }

    @Test
    void fallsBackToMaintenanceRecommendationWhenNothingApplies() {
        PostureInsights.Result result = PostureInsights.compose(
                posture(0, 0, 0, List.of()), List.of(), List.of(), NOW);
        assertThat(result.recommendations()).containsExactly(
                "No immediate actions required - maintain the current review cadence.");
    }

    @Test
    void caveatDeclaresDeterminismAndNoTrendClaims() {
        PostureInsights.Result result = PostureInsights.compose(
                posture(0, 0, 0, List.of()), List.of(), List.of(), NOW);
        assertThat(result.caveat()).contains("no generative AI").contains("never a trend");
    }
}
