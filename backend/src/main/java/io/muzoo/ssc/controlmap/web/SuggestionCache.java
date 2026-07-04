package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.ai.AiSuggestionProperties;
import io.muzoo.ssc.controlmap.web.dto.SuggestionResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * A small in-memory, per-finding cache of AI suggestions (PLAN §12: "cache suggestions per finding so
 * you don't re-call repeatedly"). A repeat "Suggest controls" click within the TTL returns instantly
 * without another Claude call — saving cost and latency. Entries expire after {@code cacheTtlSeconds};
 * {@link #invalidate} lets a later edit/accept flow (S2b) drop a stale entry. Deliberately dependency-free
 * (no cache library) to keep the CVE surface small.
 */
@Component
public class SuggestionCache {

    /** Cap on distinct cached findings, so a long-running server can't grow this map without bound. */
    private static final int MAX_ENTRIES = 500;

    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public SuggestionCache(AiSuggestionProperties properties) {
        this.ttlMillis = Math.max(0L, properties.getCacheTtlSeconds()) * 1000L;
    }

    /** Returns the cached suggestions for a finding if present and not expired. */
    public Optional<List<SuggestionResponse>> get(Long findingId) {
        Entry entry = cache.get(findingId);
        if (entry == null) {
            return Optional.empty();
        }
        if (System.currentTimeMillis() >= entry.expiresAt) {
            // remove(key, value): only drop this exact entry, not a fresher one a concurrent put stored.
            cache.remove(findingId, entry);
            return Optional.empty();
        }
        return Optional.of(entry.suggestions);
    }

    /** Caches the suggestions for a finding until the TTL elapses. */
    public void put(Long findingId, List<SuggestionResponse> suggestions) {
        // Bound the map to MAX_ENTRIES when adding a *new* finding: reclaim expired entries first, and if
        // that's not enough, drop the oldest so the cap actually holds (expired-only eviction could exceed it).
        if (cache.size() >= MAX_ENTRIES && !cache.containsKey(findingId)) {
            evictExpired();
            if (cache.size() >= MAX_ENTRIES) {
                evictOldest();
            }
        }
        cache.put(findingId, new Entry(List.copyOf(suggestions), System.currentTimeMillis() + ttlMillis));
    }

    /** Drops any cached suggestions for a finding (e.g. after it is edited or a suggestion is accepted). */
    public void invalidate(Long findingId) {
        cache.remove(findingId);
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        cache.values().removeIf(entry -> now >= entry.expiresAt);
    }

    /** Drops the entry closest to expiry (with a constant TTL, the oldest) — a last resort to hold the cap. */
    private void evictOldest() {
        cache.entrySet().stream()
                .min(Comparator.comparingLong(e -> e.getValue().expiresAt))
                .ifPresent(e -> cache.remove(e.getKey(), e.getValue()));
    }

    private record Entry(List<SuggestionResponse> suggestions, long expiresAt) {
    }
}
