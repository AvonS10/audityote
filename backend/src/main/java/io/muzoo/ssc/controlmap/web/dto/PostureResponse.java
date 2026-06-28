package io.muzoo.ssc.controlmap.web.dto;

import java.util.List;

/**
 * Program-wide risk posture rollup (PLAN §9/§10), matching the design's {@code CM_POSTURE} shape.
 * {@code score} is the 0–100 gauge (higher = worse); {@code deltaPts} is the change vs 90 days ago
 * (0 until daily snapshots exist — stretch). The breakdowns cover all active (non-deleted) findings;
 * {@code heatRows} is a severity×status cross-tab whose {@code cells} follow {@code heatStatuses}.
 */
public record PostureResponse(
        int score,
        int deltaPts,
        long total,
        long active,
        long criticalActive,
        long remediated90d,
        List<SeverityCount> bySeverity,
        List<StatusCount> byStatus,
        List<String> heatStatuses,
        List<HeatRow> heatRows) {

    /** Count of findings in a severity band (key is the lowercase wire value, e.g. {@code critical}). */
    public record SeverityCount(String key, String label, long count) {
    }

    /** Count of findings in a workflow status (key is the kebab wire value, e.g. {@code in-progress}). */
    public record StatusCount(String key, String label, long count) {
    }

    /** One heatmap row (a severity) with a count per status, ordered to match {@code heatStatuses}. */
    public record HeatRow(String key, String label, List<Long> cells) {
    }
}
