package io.muzoo.ssc.controlmap.ai;

import java.util.List;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * The real {@link SuggestionModelClient}: sends the prompts to Claude via Spring AI's {@link
 * AnthropicChatModel} (a native client for the Anthropic Messages API). This is the only Spring-AI-aware
 * class; it is built solely by {@code AiConfig} when the feature is enabled, so unit tests exercise
 * {@link ClaudeCatalogStrategy} through a fake client instead and never call out. Model, temperature,
 * and token cap are baked into the chat model's default options (see {@code AiConfig}).
 */
public class SpringAiSuggestionModelClient implements SuggestionModelClient {

    private final AnthropicChatModel chatModel;

    public SpringAiSuggestionModelClient(AnthropicChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        try {
            Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)));
            return chatModel.call(prompt).getResult().getOutput().getText();
        } catch (RuntimeException e) {
            // Network / auth / rate-limit failures surface as a clean 503 at the endpoint (S2).
            throw new MappingSuggestionException("The suggestion model call failed.", e);
        }
    }
}
