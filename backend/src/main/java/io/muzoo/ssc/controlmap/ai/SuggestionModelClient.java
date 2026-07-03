package io.muzoo.ssc.controlmap.ai;

/**
 * Narrow port (DIP) between {@link ClaudeCatalogStrategy} and the underlying LLM: given a system and a
 * user prompt, return the model's raw text response. Keeping this seam thin means the strategy's
 * prompt-building, JSON parsing, and catalog-grounding logic are all unit-testable with a fake client
 * (no live API, no spend — PLAN §12), and the real provider ({@code SpringAiSuggestionModelClient}, or
 * a future local model) is swappable without touching the strategy.
 */
public interface SuggestionModelClient {

    /**
     * Sends the prompts to the model and returns its raw completion text (expected to be a JSON array;
     * parsing and grounding are the caller's job).
     *
     * @throws MappingSuggestionException if the upstream call fails
     */
    String complete(String systemPrompt, String userPrompt);
}
