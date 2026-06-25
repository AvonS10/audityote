package io.muzoo.ssc.controlmap.domain;

/**
 * Finding workflow status. The legal transition graph and its role gating live in the State
 * machine (chunk #15, PLAN §8); this enum only enumerates the states.
 *
 * <p>Lifecycle: OPEN → IN_PROGRESS → SUBMITTED → (APPROVED | RETURNED) → REMEDIATED, plus ACCEPTED;
 * reopening REMEDIATED/ACCEPTED returns to IN_PROGRESS. New findings start OPEN.
 *
 * <p>Stored as the enum name; the API/UI use lowercase kebab (e.g. {@code in-progress}) via the Mapper.
 */
public enum FindingStatus {
    OPEN,
    IN_PROGRESS,
    SUBMITTED,
    APPROVED,
    RETURNED,
    REMEDIATED,
    ACCEPTED
}
