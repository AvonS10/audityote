package io.muzoo.ssc.controlmap.web.dto;

/**
 * One control's coverage posture (PLAN §10): how many findings map to it, the worst severity among
 * them, and whether any active high/critical finding makes it an at-risk control. {@code findingCount
 * == 0} marks a gap (an uncovered control). {@code highestSeverity} is the lowercase wire value (or
 * null when no findings map to the control).
 */
public record CoverageRow(ControlInfo control, long findingCount, String highestSeverity, boolean atRisk) {

    /** The control identity exposed on a coverage row — id, code and title only (PLAN §10). */
    public record ControlInfo(Long id, String code, String title) {
    }
}
