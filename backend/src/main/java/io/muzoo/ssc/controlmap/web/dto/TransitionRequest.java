package io.muzoo.ssc.controlmap.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * A workflow transition request (PLAN §8/§10): the {@code action} (e.g. {@code submit}, {@code return})
 * and an optional {@code comment} — required by the server only for actions that mandate one (return).
 */
public record TransitionRequest(@NotBlank String action, String comment) {
}
