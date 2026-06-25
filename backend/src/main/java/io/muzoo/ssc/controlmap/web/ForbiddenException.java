package io.muzoo.ssc.controlmap.web;

/** Thrown when the authenticated user may not perform the action (e.g. not the owner); maps to 403. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
