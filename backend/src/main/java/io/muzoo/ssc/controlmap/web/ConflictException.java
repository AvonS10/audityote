package io.muzoo.ssc.controlmap.web;

/** Thrown for an illegal action given the resource's state (e.g. editing a non-editable finding); maps to 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
