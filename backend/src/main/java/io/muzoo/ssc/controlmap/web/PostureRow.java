package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.domain.FindingStatus;
import io.muzoo.ssc.controlmap.domain.Severity;
import java.time.Instant;

/**
 * Minimal per-finding projection for the posture rollup (#19) — just the fields the gauge, breakdowns
 * and heatmap need, fetched in one query (no full entities, no N+1). Populated via a JPQL constructor
 * expression in {@code FindingRepository.findPostureRows()}.
 */
public record PostureRow(Severity severity, FindingStatus status, Instant updatedAt) {
}
