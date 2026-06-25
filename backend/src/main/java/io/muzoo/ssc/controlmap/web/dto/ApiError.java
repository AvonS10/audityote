package io.muzoo.ssc.controlmap.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Consistent error envelope returned for all API failures (PLAN §10). Never carries stack traces or
 * other internals. {@code fieldErrors} is present only for validation failures.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        List<FieldError> fieldErrors) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(Instant.now(), status, error, message, null);
    }

    public static ApiError of(int status, String error, String message, List<FieldError> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, fieldErrors);
    }

    /** A single field-level validation problem. */
    public record FieldError(String field, String message) {
    }
}
