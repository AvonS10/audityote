package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.domain.FindingStatus;
import io.muzoo.ssc.controlmap.domain.Severity;
import io.muzoo.ssc.controlmap.repository.FindingRepository;
import io.muzoo.ssc.controlmap.web.dto.PostureResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes the program-wide risk posture (PLAN §9): a 0–100 gauge plus severity/status breakdowns and
 * a severity×status heatmap, over all active (non-deleted) findings.
 *
 * <p>Score model (confirmed §16 #5): a <em>volume-sensitive</em> weighted sum of the active findings —
 * Critical 10 / High 6 / Medium 3 / Low 1 — normalised against a configurable cap, ×100, clamped 0–100.
 * The cap is a documented policy threshold (the weighted active-risk load deemed "maxed out"), set via
 * {@code controlmap.posture.cap} (default 100). {@code deltaPts} is 0 until daily snapshots exist.
 */
@Service
@Transactional(readOnly = true)
public class PostureService {

    /** Findings counted toward the live posture (§9): not yet resolved/closed. */
    private static final List<FindingStatus> ACTIVE = List.of(
            FindingStatus.OPEN, FindingStatus.IN_PROGRESS, FindingStatus.SUBMITTED, FindingStatus.RETURNED);

    /** Severity weights for the posture sum (§9 / §16 #5). */
    private static final Map<Severity, Integer> WEIGHTS = new EnumMap<>(Map.of(
            Severity.CRITICAL, 10, Severity.HIGH, 6, Severity.MEDIUM, 3, Severity.LOW, 1));

    /** Display order: Critical → Low for severity rows/bars. */
    private static final List<Severity> SEVERITY_ORDER = List.of(
            Severity.CRITICAL, Severity.HIGH, Severity.MEDIUM, Severity.LOW);

    /** Display order: workflow order for status breakdown + heatmap columns. */
    private static final List<FindingStatus> STATUS_ORDER = List.of(
            FindingStatus.OPEN, FindingStatus.IN_PROGRESS, FindingStatus.SUBMITTED, FindingStatus.RETURNED,
            FindingStatus.APPROVED, FindingStatus.REMEDIATED, FindingStatus.ACCEPTED);

    private static final Duration REMEDIATION_WINDOW = Duration.ofDays(90);

    private final FindingRepository findings;
    private final int cap;

    public PostureService(FindingRepository findings,
                          @Value("${controlmap.posture.cap:100}") int cap) {
        this.findings = findings;
        this.cap = cap > 0 ? cap : 100;
    }

    public PostureResponse rollup() {
        return rollup(findings.findPostureRows(), Instant.now());
    }

    /** Pure computation over the projected rows — separated from the query so it is unit-testable. */
    PostureResponse rollup(List<PostureRow> rows, Instant now) {
        long total = rows.size();
        long active = rows.stream().filter(r -> ACTIVE.contains(r.status())).count();
        long criticalActive = rows.stream()
                .filter(r -> ACTIVE.contains(r.status()) && r.severity() == Severity.CRITICAL).count();
        Instant since = now.minus(REMEDIATION_WINDOW);
        long remediated90d = rows.stream()
                .filter(r -> r.status() == FindingStatus.REMEDIATED
                        && r.updatedAt() != null && !r.updatedAt().isBefore(since)).count();

        int weightedSum = rows.stream()
                .filter(r -> ACTIVE.contains(r.status()))
                .mapToInt(r -> WEIGHTS.getOrDefault(r.severity(), 0)).sum();
        int score = clamp(Math.round((weightedSum / (float) cap) * 100f));

        List<PostureResponse.SeverityCount> bySeverity = SEVERITY_ORDER.stream()
                .map(s -> new PostureResponse.SeverityCount(
                        FindingMapper.severityToWire(s), label(s),
                        rows.stream().filter(r -> r.severity() == s).count()))
                .toList();

        List<PostureResponse.StatusCount> byStatus = STATUS_ORDER.stream()
                .map(st -> new PostureResponse.StatusCount(
                        FindingMapper.statusToWire(st), label(st),
                        rows.stream().filter(r -> r.status() == st).count()))
                .toList();

        List<String> heatStatuses = STATUS_ORDER.stream().map(FindingMapper::statusToWire).toList();
        List<PostureResponse.HeatRow> heatRows = SEVERITY_ORDER.stream()
                .map(s -> new PostureResponse.HeatRow(
                        FindingMapper.severityToWire(s), label(s),
                        STATUS_ORDER.stream()
                                .map(st -> rows.stream()
                                        .filter(r -> r.severity() == s && r.status() == st).count())
                                .toList()))
                .toList();

        return new PostureResponse(score, 0, total, active, criticalActive, remediated90d,
                bySeverity, byStatus, heatStatuses, heatRows);
    }

    private static int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    /** Title-case label from an enum constant (e.g. {@code IN_PROGRESS} → "In Progress"). */
    private static String label(Enum<?> value) {
        String[] words = value.name().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
