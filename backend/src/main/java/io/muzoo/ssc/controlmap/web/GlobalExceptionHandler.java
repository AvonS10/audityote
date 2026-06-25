package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.web.dto.ApiError;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions into the consistent {@link ApiError} model (PLAN §10) without leaking
 * internals. Validation → 400 with field errors; failed authentication → a generic 401 (the message
 * never reveals whether the email or the password was wrong).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ApiError body = ApiError.of(HttpStatus.BAD_REQUEST.value(), "Bad Request",
                "Validation failed.", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex) {
        ApiError body = ApiError.of(HttpStatus.UNAUTHORIZED.value(), "Unauthorized",
                "Invalid email or password.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }
}
