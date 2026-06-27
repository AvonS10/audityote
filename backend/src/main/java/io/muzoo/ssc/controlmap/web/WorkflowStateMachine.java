package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.domain.FindingStatus;
import io.muzoo.ssc.controlmap.domain.Role;
import io.muzoo.ssc.controlmap.workflow.FindingWorkflowState;
import io.muzoo.ssc.controlmap.workflow.WorkflowAction;
import io.muzoo.ssc.controlmap.workflow.WorkflowTransition;
import org.springframework.stereotype.Component;

/**
 * The context of the Finding State machine (PLAN §8): given the current state and a requested action,
 * it asks the {@link FindingWorkflowState} for the matching transition and enforces its rules
 * server-side — legality (409), actor/role + separation of duties (403), required comment (400), and
 * the submit guard (409). The legal edges live on the state; this class only enforces them.
 */
@Component
public class WorkflowStateMachine {

    /**
     * Validates and resolves the target status for a transition. Throws (never returns) when the
     * transition is illegal for the current state, the actor is not allowed, a required comment is
     * missing, or the submit guard (≥1 mapped control) is unmet.
     */
    public FindingStatus next(FindingStatus current, WorkflowAction action, String comment,
                              boolean actorIsOwner, Role actorRole, long mappedControlCount) {
        WorkflowTransition transition = FindingWorkflowState.of(current).transitionFor(action)
                .orElseThrow(() -> new ConflictException(
                        "Cannot " + action.wire() + " a finding that is " + FindingMapper.statusToWire(current) + "."));

        requireActor(transition, action, actorIsOwner, actorRole);

        if (transition.commentRequired() && (comment == null || comment.isBlank())) {
            throw new BadRequestException("A comment is required to " + action.wire() + " this finding.");
        }
        if (transition.requiresMappedControl() && mappedControlCount == 0) {
            throw new ConflictException("Map at least one control before submitting for review.");
        }
        return transition.target();
    }

    private static void requireActor(WorkflowTransition transition, WorkflowAction action, boolean isOwner, Role role) {
        switch (transition.actor()) {
            case OWNER -> {
                if (!isOwner) {
                    throw new ForbiddenException("Only the owning analyst can " + action.wire() + " this finding.");
                }
            }
            case REVIEWER -> {
                if (role != Role.REVIEWER) {
                    throw new ForbiddenException("Only a reviewer can " + action.wire() + " a finding.");
                }
                // Separation of duties: the reviewer deciding must not be the finding's owner.
                if (isOwner) {
                    throw new ForbiddenException(
                            "A reviewer cannot " + action.wire() + " their own finding (separation of duties).");
                }
            }
        }
    }
}
