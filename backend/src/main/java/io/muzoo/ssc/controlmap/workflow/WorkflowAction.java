package io.muzoo.ssc.controlmap.workflow;

import java.util.Locale;

/**
 * A workflow transition the client can request via {@code POST /api/findings/{id}/transition}
 * (PLAN §8). The wire value is the lowercase name (e.g. {@code submit}, {@code return}); each action
 * is realised by exactly one {@link WorkflowTransition} on the current {@link FindingWorkflowState}.
 */
public enum WorkflowAction {
    SUBMIT,
    APPROVE,
    RETURN,
    RESUBMIT,
    REMEDIATE,
    ACCEPT,
    REOPEN;

    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Resolves the {@code action} request value; unknown actions throw IllegalArgumentException. */
    public static WorkflowAction fromWire(String value) {
        return WorkflowAction.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
