package io.muzoo.ssc.controlmap.workflow;

import io.muzoo.ssc.controlmap.domain.FindingStatus;

/**
 * One legal edge out of a {@link FindingWorkflowState} (PLAN §8): the triggering {@link WorkflowAction},
 * the resulting status, who may perform it, and any guards. The state owns its transitions; the engine
 * ({@code WorkflowStateMachine}) enforces these rules. Keeping the rules <em>on the transition</em> is
 * what makes this the State pattern rather than a scattered if/else.
 *
 * @param actor                 OWNER = the owning analyst; REVIEWER = a reviewer who is not the owner
 *                              (separation of duties).
 * @param commentRequired       a non-empty comment must accompany the action (e.g. "return for changes").
 * @param requiresMappedControl the finding must have at least one mapped control (the submit guard, §16).
 */
public record WorkflowTransition(
        WorkflowAction action,
        FindingStatus target,
        Actor actor,
        boolean commentRequired,
        boolean requiresMappedControl) {

    /** Who is allowed to perform a transition. */
    public enum Actor {
        OWNER,
        REVIEWER
    }
}
