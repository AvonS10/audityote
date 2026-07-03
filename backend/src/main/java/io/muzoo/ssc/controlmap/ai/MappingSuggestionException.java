package io.muzoo.ssc.controlmap.ai;

/**
 * Thrown when a suggestion request cannot be fulfilled — the model returned unparseable output, or the
 * upstream call failed. The suggest-controls endpoint (S2) maps this to {@code 503} so the UI can fall
 * back to a Banner and manual mapping (PLAN §7.12/§10); the graded core never depends on AI succeeding.
 */
public class MappingSuggestionException extends RuntimeException {

    public MappingSuggestionException(String message, Throwable cause) {
        super(message, cause);
    }

    public MappingSuggestionException(String message) {
        super(message);
    }
}
