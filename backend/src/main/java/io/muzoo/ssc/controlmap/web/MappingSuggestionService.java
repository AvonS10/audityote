package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.ai.AiSuggestionProperties;
import io.muzoo.ssc.controlmap.ai.MappingSuggestionException;
import io.muzoo.ssc.controlmap.ai.MappingSuggestionStrategy;
import io.muzoo.ssc.controlmap.domain.Control;
import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.repository.ControlRepository;
import io.muzoo.ssc.controlmap.web.dto.FindingDetail;
import io.muzoo.ssc.controlmap.web.dto.SuggestionResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates AI control suggestions for a finding (PLAN §4/§10) — the read side of the stretch
 * feature. Kept separate from {@link FindingService} (SRP): that owns finding CRUD; this owns the AI
 * concerns (enablement, rate limiting, caching, grounding-to-DTO).
 *
 * <p><b>The model call runs outside any transaction.</b> {@link #suggest} authorizes and loads its inputs
 * in short, separate transactions (the owner check in {@link FindingService}; the catalog via
 * {@link ControlRepository#findAllWithFramework()}, which fetches each control's framework so it stays
 * readable once detached), then invokes the strategy with those detached inputs. This keeps the
 * multi-second Claude call from pinning a Hikari connection for its whole duration — a class-level
 * {@code @Transactional} here would hold one open across the network call and could exhaust the pool under
 * concurrent uncached suggests. {@link #accept}, which only touches the DB, keeps its own transaction.
 *
 * <p><b>Enablement is bean presence:</b> the {@link MappingSuggestionStrategy} bean exists only when
 * {@code controlmap.ai.enabled=true} built it (S1's {@code AiConfig}). Absent → the feature is off; a
 * strategy failure throws the same exception — both surface as a generic {@code 503} so the UI falls
 * back to manual mapping (PLAN §7.12). Suggestions never bypass the workflow; they are applied only when
 * an analyst accepts one (S2b).
 */
@Service
public class MappingSuggestionService {

    private final FindingService findingService;
    private final ControlRepository controls;
    private final ObjectProvider<MappingSuggestionStrategy> strategyProvider;
    private final CatalogMapper catalogMapper;
    private final SuggestionCache cache;
    private final SuggestionRateLimiter rateLimiter;
    private final AiSuggestionProperties properties;

    public MappingSuggestionService(FindingService findingService, ControlRepository controls,
                                    ObjectProvider<MappingSuggestionStrategy> strategyProvider,
                                    CatalogMapper catalogMapper, SuggestionCache cache,
                                    SuggestionRateLimiter rateLimiter, AiSuggestionProperties properties) {
        this.findingService = findingService;
        this.controls = controls;
        this.strategyProvider = strategyProvider;
        this.catalogMapper = catalogMapper;
        this.cache = cache;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
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

        // Fetch the catalog with frameworks so it (and the eager-fielded finding) stay readable while the
        // strategy runs — the Claude call happens here, with no transaction/connection held open.
        List<Control> catalog = controls.findAllWithFramework();
        List<SuggestionResponse> suggestions = strategy.suggest(finding, catalog).stream()
                .map(s -> new SuggestionResponse(catalogMapper.toControlResponse(s.control()),
                        s.confidence(), s.rationale()))
                .toList();

        cache.put(findingId, suggestions);
        return suggestions;
    }

    /**
     * Accepts an AI-suggested control, creating an {@code AI_SUGGESTED} mapping (S2b). Provenance is
     * <b>server-authoritative</b>: the control must be a current cached suggestion for this finding, and
     * its confidence/rationale are copied from that cached suggestion (the model id from config) — never
     * taken from the request. A control the server didn't suggest (or a suggestion that has since expired
     * from the cache) is rejected, so the audit trail can only ever reflect genuine machine origin.
     *
     * @throws ConflictException if the control is not a current cached suggestion for the finding (→ 409)
     */
    @Transactional
    public FindingDetail accept(Long findingId, Long controlId, String callerEmail) {
        // Authorise first (404/403/409) so a non-owner learns nothing about another user's cache state.
        findingService.requireSuggestable(findingId, callerEmail);

        SuggestionResponse suggestion = cache.get(findingId).stream()
                .flatMap(List::stream)
                .filter(s -> s.control().id().equals(controlId))
                .findFirst()
                .orElseThrow(() -> new ConflictException("That control is not a current AI suggestion for "
                        + "this finding. Re-run suggestions and try again."));

        return findingService.addAiMapping(findingId, controlId, suggestion.confidence(),
                suggestion.rationale(), properties.getModel(), callerEmail);
    }
}
