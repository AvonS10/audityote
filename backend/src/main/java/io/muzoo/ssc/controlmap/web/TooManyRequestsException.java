package io.muzoo.ssc.controlmap.web;

/**
 * The caller has exceeded the suggestion rate limit (PLAN §12). Mapped to HTTP 429 by
 * {@link GlobalExceptionHandler}, so a burst of "Suggest controls" clicks can't run up the AI bill.
 */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
