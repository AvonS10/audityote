package io.muzoo.ssc.controlmap.workflow;

import io.muzoo.ssc.controlmap.domain.FindingStatus;
import io.muzoo.ssc.controlmap.workflow.WorkflowTransition.Actor;
import java.util.List;
import java.util.Optional;

/**
 * The Finding workflow as the State pattern (PLAN §8): one constant per {@link FindingStatus}, each
 * declaring the transitions legal <em>from</em> that state. Behaviour lives with the state, so the
 * engine just asks the current state "can I do X here?" instead of branching on status. Mirrors
 * {@code FindingStatus} 1:1 by name, so {@link #of(FindingStatus)} bridges the persisted enum.
 */
public enum FindingWorkflowState {

    OPEN(List.of(submit())),
    IN_PROGRESS(List.of(submit())),
    SUBMITTED(List.of(
            new WorkflowTransition(WorkflowAction.APPROVE, FindingStatus.APPROVED, Actor.REVIEWER, false, false),
            new WorkflowTransition(WorkflowAction.RETURN, FindingStatus.RETURNED, Actor.REVIEWER, true, false))),
    RETURNED(List.of(
            // Resubmit re-enters review, so it carries the same "must have a mapped control" guard as submit.
            new WorkflowTransition(WorkflowAction.RESUBMIT, FindingStatus.SUBMITTED, Actor.OWNER, false, true))),
    APPROVED(List.of(
            new WorkflowTransition(WorkflowAction.REMEDIATE, FindingStatus.REMEDIATED, Actor.OWNER, false, false),
            new WorkflowTransition(WorkflowAction.ACCEPT, FindingStatus.ACCEPTED, Actor.REVIEWER, false, false))),
    REMEDIATED(List.of(reopen())),
    ACCEPTED(List.of(reopen()));

    private final List<WorkflowTransition> transitions;

    FindingWorkflowState(List<WorkflowTransition> transitions) {
        this.transitions = transitions;
    }

    public List<WorkflowTransition> transitions() {
        return transitions;
    }

    public Optional<WorkflowTransition> transitionFor(WorkflowAction action) {
        return transitions.stream().filter(t -> t.action() == action).findFirst();
    }

    /** Bridges the persisted {@link FindingStatus} to its behavioural state (same names). */
    public static FindingWorkflowState of(FindingStatus status) {
        return valueOf(status.name());
    }

    // Shared transition definitions (OPEN and IN_PROGRESS both submit; REMEDIATED and ACCEPTED both reopen).
    private static WorkflowTransition submit() {
        return new WorkflowTransition(WorkflowAction.SUBMIT, FindingStatus.SUBMITTED, Actor.OWNER, false, true);
    }

    private static WorkflowTransition reopen() {
        return new WorkflowTransition(WorkflowAction.REOPEN, FindingStatus.IN_PROGRESS, Actor.OWNER, false, false);
    }
}
