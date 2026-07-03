package io.muzoo.ssc.controlmap.ai;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the AI control-mapping beans — but <b>only when {@code controlmap.ai.enabled=true}</b>. With the
 * flag off (the default, and CI) none of these beans exist: no Claude client is constructed and no
 * {@code ANTHROPIC_API_KEY} is needed, so the app boots and the graded core runs with zero AI
 * dependency. Because the plain {@code spring-ai-anthropic} module ships no auto-configuration, this
 * class is the single, explicit place the Claude client is created (PLAN §4/§12).
 *
 * <p>When the flag is off, no {@link MappingSuggestionStrategy} bean is present; the suggest-controls
 * service (S2) treats that as "disabled" and returns {@code 503}.
 */
@Configuration
@ConditionalOnProperty(prefix = "controlmap.ai", name = "enabled", havingValue = "true")
public class AiConfig {

    @Bean
    AnthropicApi anthropicApi(AiSuggestionProperties properties) {
        return AnthropicApi.builder().apiKey(properties.getApiKey()).build();
    }

    @Bean
    AnthropicChatModel anthropicChatModel(AnthropicApi anthropicApi, AiSuggestionProperties properties) {
        return AnthropicChatModel.builder()
                .anthropicApi(anthropicApi)
                .defaultOptions(AnthropicChatOptions.builder()
                        .model(properties.getModel())
                        .temperature(properties.getTemperature())
                        .maxTokens(properties.getMaxTokens())
                        .build())
                .build();
    }

    @Bean
    SuggestionModelClient suggestionModelClient(AnthropicChatModel anthropicChatModel) {
        return new SpringAiSuggestionModelClient(anthropicChatModel);
    }

    @Bean
    MappingSuggestionStrategy mappingSuggestionStrategy(SuggestionModelClient client,
                                                        AiSuggestionProperties properties) {
        return new ClaudeCatalogStrategy(client, properties);
    }
}
