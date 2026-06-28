package io.muzoo.ssc.controlmap.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.muzoo.ssc.controlmap.domain.FindingStatus;
import io.muzoo.ssc.controlmap.domain.Severity;
import io.muzoo.ssc.controlmap.web.dto.PostureResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Posture rollup math (PLAN §9): the weighted-sum/cap gauge, the active vs total split, the 90-day
 * remediation window, and the severity×status cross-tab. Pure unit test over hand-built rows (the
 * repository is not involved), with a fixed {@code now} and cap 100.
 */
class PostureServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-28T00:00:00Z");
    private final PostureService service = new PostureService(null, 100);

    private static PostureRow row(Severity severity, FindingStatus status) {
        return new PostureRow(severity, status, NOW);
    }

    @Test
    void scoreIsWeightedActiveSumOverCap() {
        // 2 critical (10) + 2 high (6) + 3 medium (3) active = 20 + 12 + 9 = 41 -> 41/100*100 = 41.
        List<PostureRow> rows = new ArrayList<>();
        rows.add(row(Severity.CRITICAL, FindingStatus.OPEN));
        rows.add(row(Severity.CRITICAL, FindingStatus.IN_PROGRESS));
        rows.add(row(Severity.HIGH, FindingStatus.SUBMITTED));
        rows.add(row(Severity.HIGH, FindingStatus.RETURNED));
        rows.add(row(Severity.MEDIUM, FindingStatus.OPEN));
        rows.add(row(Severity.MEDIUM, FindingStatus.OPEN));
        rows.add(row(Severity.MEDIUM, FindingStatus.IN_PROGRESS));

        PostureResponse p = service.rollup(rows, NOW);
        assertThat(p.score()).isEqualTo(41);
        assertThat(p.active()).isEqualTo(7);
        assertThat(p.total()).isEqualTo(7);
        assertThat(p.criticalActive()).isEqualTo(2);
        assertThat(p.deltaPts()).isZero();
    }

    @Test
    void resolvedFindingsAreExcludedFromScoreButCountInTotal() {
        // Approved/remediated/accepted are not "active": they don't add to the score, but do to total.
        List<PostureRow> rows = List.of(
                row(Severity.CRITICAL, FindingStatus.OPEN),       // active, weight 10
                row(Severity.CRITICAL, FindingStatus.APPROVED),   // not active
                row(Severity.CRITICAL, FindingStatus.REMEDIATED), // not active
                row(Severity.CRITICAL, FindingStatus.ACCEPTED));  // not active

        PostureResponse p = service.rollup(rows, NOW);
        assertThat(p.score()).isEqualTo(10);
        assertThat(p.active()).isEqualTo(1);
        assertThat(p.total()).isEqualTo(4);
        assertThat(p.criticalActive()).isEqualTo(1);
    }

    @Test
    void scoreClampsToHundred() {
        // 11 active criticals = 110 weighted -> clamped to 100.
        List<PostureRow> rows = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            rows.add(row(Severity.CRITICAL, FindingStatus.OPEN));
        }
        assertThat(service.rollup(rows, NOW).score()).isEqualTo(100);
    }

    @Test
    void remediatedWindowCountsOnlyRecent() {
        PostureRow recent = new PostureRow(Severity.HIGH, FindingStatus.REMEDIATED, NOW.minus(10, ChronoUnit.DAYS));
        PostureRow old = new PostureRow(Severity.HIGH, FindingStatus.REMEDIATED, NOW.minus(120, ChronoUnit.DAYS));
        PostureResponse p = service.rollup(List.of(recent, old), NOW);
        assertThat(p.remediated90d()).isEqualTo(1);
    }

    @Test
    void breakdownsAndHeatmapCoverAllBandsInOrder() {
        List<PostureRow> rows = List.of(
                row(Severity.CRITICAL, FindingStatus.OPEN),
                row(Severity.LOW, FindingStatus.ACCEPTED));

        PostureResponse p = service.rollup(rows, NOW);
        // Severity bars: Critical → Low, every band present (count 0 allowed).
        assertThat(p.bySeverity()).extracting(PostureResponse.SeverityCount::key)
                .containsExactly("critical", "high", "medium", "low");
        // Status breakdown + heatmap columns: full workflow order.
        assertThat(p.heatStatuses()).containsExactly(
                "open", "in-progress", "submitted", "returned", "approved", "remediated", "accepted");
        assertThat(p.byStatus()).extracting(PostureResponse.StatusCount::label)
                .containsExactly("Open", "In Progress", "Submitted", "Returned", "Approved", "Remediated", "Accepted");
        // Heat cross-tab: the critical row has its single OPEN finding in the first column.
        PostureResponse.HeatRow critical = p.heatRows().get(0);
        assertThat(critical.key()).isEqualTo("critical");
        assertThat(critical.cells().get(0)).isEqualTo(1L);
        assertThat(critical.cells().get(1)).isEqualTo(0L);
    }
}
