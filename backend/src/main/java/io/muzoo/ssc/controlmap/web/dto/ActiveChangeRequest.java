package io.muzoo.ssc.controlmap.web.dto;

import jakarta.validation.constraints.NotNull;

/** Admin deactivates/reactivates a user (#admin). {@code active} is required (no accidental default). */
public record ActiveChangeRequest(@NotNull(message = "active is required.") Boolean active) {
}
