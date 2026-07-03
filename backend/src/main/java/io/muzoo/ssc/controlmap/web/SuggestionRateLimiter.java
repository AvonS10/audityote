package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.ai.AiSuggestionProperties;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * A simple per-user, fixed-window rate limiter for the suggest-controls endpoint (PLAN §12): at most
 * {@code rateLimitPerMinute} calls per user per rolling 60-second window. This caps how fast one user
 * can drive paid Claude calls (a burst of clicks, or a script). Deliberately in-memory and
 * dependency-free — adequate for a single-node deployment; a distributed limiter (e.g. Redis) would be
 * the scale-out upgrade.
 */
@Component
public class SuggestionRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final int limitPerWindow;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public SuggestionRateLimiter(AiSuggestionProperties properties) {
        this.limitPerWindow = Math.max(1, properties.getRateLimitPerMinute());
    }

    /**
     * Records one call for the user and throws {@link TooManyRequestsException} if it exceeds the limit
     * for the current window. Call this only for requests that will actually reach the model (not cache
     * hits), so cached re-clicks stay free.
     */
    public void checkAndConsume(String user) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(user, (key, current) ->
                (current == null || now - current.startMillis >= WINDOW_MILLIS)
                        ? new Window(now, 1)
                        : new Window(current.startMillis, current.count + 1));
        if (window.count > limitPerWindow) {
            throw new TooManyRequestsException(
                    "Too many suggestion requests. Please wait a moment and try again.");
        }
    }

    private record Window(long startMillis, int count) {
    }
}
