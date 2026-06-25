package io.muzoo.ssc.controlmap.web;

/** Thrown for invalid client input (e.g. an unknown filter value); mapped to a 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
