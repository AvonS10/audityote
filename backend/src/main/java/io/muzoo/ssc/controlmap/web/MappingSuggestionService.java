package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.ai.MappingSuggestionException;
import io.muzoo.ssc.controlmap.ai.MappingSuggestionStrategy;
import io.muzoo.ssc.controlmap.domain.Control;
import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.repository.ControlRepository;
import io.muzoo.ssc.controlmap.web.dto.SuggestionResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates AI control suggestions for a finding (PLAN §4/§10) — the read side of the stretch
 * feature. Kept separate from {@link FindingService} (SRP): that owns finding CRUD; this owns the AI
 * concerns (enablement, rate limiting, caching, grounding-to-DTO). Runs {@code @Transactional} so the
 * catalog's lazy associations load while the strategy builds its prompt (a lesson from the live smoke test).
 *
 * <p><b>Enablement is bean presence:</b> the {@link MappingSuggestionStrategy} bean exists only when
 * {@code controlmap.ai.enabled=true} built it (S1's {@code AiConfig}). Absent → the feature is off; a
 * strategy failure throws the same exception — both surface as a generic {@code 503} so the UI falls
 * back to manual mapping (PLAN §7.12). Suggestions never bypass the workflow; they are applied only when
 * an analyst accepts one (S2b).
 */
@Service
@Transactional(readOnly = true)
public class MappingSuggestionService {

    private final FindingService findingService;
    private final ControlRepository controls;
    private final ObjectProvider<MappingSuggestionStrategy> strategyProvider;
    private final CatalogMapper catalogMapper;
    private final SuggestionCache cache;
    private final SuggestionRateLimiter rateLimiter;

    public MappingSuggestionService(FindingService findingService, ControlRepository controls,
                                    ObjectProvider<MappingSuggestionStrategy> strategyProvider,
                                    CatalogMapper catalogMapper, SuggestionCache cache,
                                    SuggestionRateLimiter rateLimiter) {
        this.findingService = findingService;
        this.controls = controls;
        this.strategyProvider = strategyProvider;
        this.catalogMapper = catalogMapper;
        this.cache = cache;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Returns grounded control suggestions for a finding the caller owns and may still edit.
     *
     * @throws MappingSuggestionException if the feature is disabled or the model call fails (→ 503)
     * @throws TooManyRequestsException   if the caller has exceeded the rate limit (→ 429)
     */
    public List<SuggestionResponse> suggest(Long findingId, String callerEmail) {
        MappingSuggestionStrategy strategy = strategyProvider.getIfAvailable();
        if (strategy == null) {
            // Feature off (no strategy bean). Generic message — don't reveal disabled-vs-failed.
            throw new MappingSuggestionException("AI control suggestions are unavailable.");
        }

        // Authorize before spending anything: must exist, be owned by the caller, and be editable.
        Finding finding = findingService.requireSuggestable(findingId, callerEmail);

        // A cached result is free — return it without touching the rate limit or the model.
        Optional<List<SuggestionResponse>> cached = cache.get(findingId);
        if (cached.isPresent()) {
            return cached.get();
        }

        // Only a real, uncached call counts against the rate limit.
        rateLimiter.checkAndConsume(callerEmail);

        List<Control> catalog = controls.findAll();
        List<SuggestionResponse> suggestions = strategy.suggest(finding, catalog).stream()
                .map(s -> new SuggestionResponse(catalogMapper.toControlResponse(s.control()),
                        s.confidence(), s.rationale()))
                .toList();

        cache.put(findingId, suggestions);
        return suggestions;
    }
}
