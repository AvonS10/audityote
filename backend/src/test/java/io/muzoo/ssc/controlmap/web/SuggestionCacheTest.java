package io.muzoo.ssc.controlmap.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.muzoo.ssc.controlmap.ai.AiSuggestionProperties;
import io.muzoo.ssc.controlmap.web.dto.ControlResponse;
import io.muzoo.ssc.controlmap.web.dto.SuggestionResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Per-finding suggestion cache (PLAN §12): hit within TTL, miss when unknown/invalidated/expired. */
class SuggestionCacheTest {

    private static final List<SuggestionResponse> SAMPLE = List.of(
            new SuggestionResponse(new ControlResponse(1L, "owasp", "A03", "Injection", "d", null), 0.9, "r"));

    private static SuggestionCache cache(int ttlSeconds) {
        AiSuggestionProperties props = new AiSuggestionProperties();
        props.setCacheTtlSeconds(ttlSeconds);
        return new SuggestionCache(props);
    }

    @Test
    void returnsCachedValueWithinTtl() {
        SuggestionCache cache = cache(600);
        cache.put(1L, SAMPLE);
        assertThat(cache.get(1L)).contains(SAMPLE);
    }

    @Test
    void missesForUnknownFinding() {
        assertThat(cache(600).get(42L)).isEmpty();
    }

    @Test
    void invalidateDropsTheEntry() {
        SuggestionCache cache = cache(600);
        cache.put(1L, SAMPLE);
        cache.invalidate(1L);
        assertThat(cache.get(1L)).isEmpty();
    }

    @Test
    void expiredEntryIsNotReturned() {
        SuggestionCache cache = cache(0); // ttl 0 → the entry is already expired on read
        cache.put(1L, SAMPLE);
        assertThat(cache.get(1L)).isEmpty();
    }
}
