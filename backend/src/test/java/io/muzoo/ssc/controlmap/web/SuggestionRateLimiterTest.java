package io.muzoo.ssc.controlmap.web;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.muzoo.ssc.controlmap.ai.AiSuggestionProperties;
import org.junit.jupiter.api.Test;

/** Per-user fixed-window rate limiting (PLAN §12): allows up to the limit, then throws; users are independent. */
class SuggestionRateLimiterTest {

    private static SuggestionRateLimiter limiter(int perMinute) {
        AiSuggestionProperties props = new AiSuggestionProperties();
        props.setRateLimitPerMinute(perMinute);
        return new SuggestionRateLimiter(props);
    }

    @Test
    void allowsUpToTheLimitThenBlocks() {
        SuggestionRateLimiter limiter = limiter(2);
        assertThatCode(() -> limiter.checkAndConsume("u")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.checkAndConsume("u")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.checkAndConsume("u")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void budgetsArePerUser() {
        SuggestionRateLimiter limiter = limiter(1);
        assertThatCode(() -> limiter.checkAndConsume("a")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.checkAndConsume("a")).isInstanceOf(TooManyRequestsException.class);
        // A different user has their own budget.
        assertThatCode(() -> limiter.checkAndConsume("b")).doesNotThrowAnyException();
    }
}
