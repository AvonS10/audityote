package io.muzoo.ssc.controlmap.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Usage;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The real {@link SuggestionModelClient}: calls the Anthropic Messages API via the official Anthropic
 * Java SDK. The only provider-aware class — built solely by {@code AiConfig} when the feature is
 * enabled, so unit tests exercise {@link ClaudeCatalogStrategy} through a fake client and never spend.
 *
 * <p><b>Prompt caching (C2).</b> The {@code systemPrompt} — the fixed instructions plus the whole
 * control catalog, identical for every finding — is sent as a system block marked
 * {@code cache_control: ephemeral}, while the varying {@code userPrompt} (the finding) follows it. On
 * the first call Anthropic caches that prefix; subsequent calls within the cache TTL (~5 min, refreshed
 * on each hit) read it back at ~0.1× the input-token price instead of re-billing the ~8–15k catalog
 * tokens. The prefix must be byte-identical to hit, which is why the catalog is rendered in a stable
 * order (see {@code ControlRepository.findAllWithFramework}). Caching only engages once the prefix
 * clears Haiku 4.5's 4096-token minimum — which the full-framework catalog does. This is a layer below
 * the per-finding {@code SuggestionCache}: that avoids the call entirely on a repeat of the same
 * finding; this makes the calls that do happen (different findings, different users) cheap by reusing
 * the catalog prefix.
 */
public class AnthropicSdkSuggestionModelClient implements SuggestionModelClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicSdkSuggestionModelClient.class);

    private final AnthropicClient client;
    private final AiSuggestionProperties properties;

    public AnthropicSdkSuggestionModelClient(AnthropicClient client, AiSuggestionProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(properties.getModel())
                    .maxTokens(properties.getMaxTokens())
                    .temperature(properties.getTemperature())
                    // The stable prefix (instructions + catalog) is a cacheable system block; the varying
                    // finding is the user message, after the cache breakpoint.
                    .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                            .text(systemPrompt)
                            .cacheControl(CacheControlEphemeral.builder().build())
                            .build()))
                    .addUserMessage(userPrompt)
                    .build();

            Message response = client.messages().create(params);
            logCacheUsage(response.usage());
            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(TextBlock::text)
                    .collect(Collectors.joining());
        } catch (RuntimeException e) {
            // Network / auth / rate-limit failures surface as a clean 503 at the endpoint (S2).
            throw new MappingSuggestionException("The suggestion model call failed.", e);
        }
    }

    /** Logs cache activity so a cache hit ({@code cacheRead > 0}) is observable — the payoff of C2. */
    private static void logCacheUsage(Usage usage) {
        if (log.isDebugEnabled()) {
            log.debug("Claude suggest tokens: input={} cacheRead={} cacheWrite={}",
                    usage.inputTokens(),
                    usage.cacheReadInputTokens().orElse(0L),
                    usage.cacheCreationInputTokens().orElse(0L));
        }
    }
}
