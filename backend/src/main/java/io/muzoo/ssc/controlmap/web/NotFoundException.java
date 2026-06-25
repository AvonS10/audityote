package io.muzoo.ssc.controlmap.web;

/** Thrown when a requested resource does not exist; mapped to a 404 by the GlobalExceptionHandler. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
