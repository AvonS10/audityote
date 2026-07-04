package io.muzoo.ssc.controlmap.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the AI control-mapping beans — but <b>only when {@code controlmap.ai.enabled=true}</b>. With the
 * flag off (the default, and CI) none of these beans exist: no Claude client is constructed and no
 * {@code ANTHROPIC_API_KEY} is needed, so the app boots and the graded core runs with zero AI
 * dependency. The official Anthropic Java SDK ships no auto-configuration, so this class is the single,
 * explicit place the Claude client is created (PLAN §4/§12; C2).
 *
 * <p>When the flag is off, no {@link MappingSuggestionStrategy} bean is present; the suggest-controls
 * service (S2) treats that as "disabled" and returns {@code 503}.
 */
@Configuration
@ConditionalOnProperty(prefix = "controlmap.ai", name = "enabled", havingValue = "true")
public class AiConfig {

    @Bean
    AnthropicClient anthropicClient(AiSuggestionProperties properties) {
        return AnthropicOkHttpClient.builder().apiKey(properties.getApiKey()).build();
    }

    @Bean
    SuggestionModelClient suggestionModelClient(AnthropicClient anthropicClient, AiSuggestionProperties properties) {
        return new AnthropicSdkSuggestionModelClient(anthropicClient, properties);
    }

    @Bean
    MappingSuggestionStrategy mappingSuggestionStrategy(SuggestionModelClient client,
                                                        AiSuggestionProperties properties) {
        return new ClaudeCatalogStrategy(client, properties);
    }
}
