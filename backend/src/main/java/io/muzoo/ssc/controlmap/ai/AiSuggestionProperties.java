package io.muzoo.ssc.controlmap.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the AI control-mapping stretch feature (PLAN §4/§12), bound from the environment.
 * The feature is <b>off by default</b>: with {@code enabled=false} no Claude client is constructed
 * (see {@code AiConfig}) and no {@code apiKey} is required, so the app boots and CI stays green without
 * any external dependency. The API key is a backend-only secret (never shipped to the browser).
 */
@ConfigurationProperties(prefix = "controlmap.ai")
public class AiSuggestionProperties {

    /** Master feature flag ({@code AI_SUGGESTIONS_ENABLED}). Off by default — the app runs fully without AI. */
    private boolean enabled = false;

    /** Anthropic API key ({@code ANTHROPIC_API_KEY}) — backend secret; only read when {@link #enabled}. */
    private String apiKey = "";

    /** Claude model id ({@code ANTHROPIC_MODEL}); Haiku 4.5 is the cheap/fast tier suited to ranking. */
    private String model = "claude-haiku-4-5-20251001";

    /** Low temperature for stable, near-deterministic control ranking. */
    private double temperature = 0.1;

    /** Response token cap — a handful of short suggestions never needs more. */
    private int maxTokens = 1024;

    /** How many grounded suggestions to return at most, best-confidence first. */
    private int maxSuggestions = 6;

    /** Per-user rate limit for the suggest-controls endpoint (calls per 60s window) — caps the AI bill. */
    private int rateLimitPerMinute = 10;

    /** How long a finding's suggestions are cached, so repeat clicks don't re-call the model. */
    private int cacheTtlSeconds = 600;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getMaxSuggestions() {
        return maxSuggestions;
    }

    public void setMaxSuggestions(int maxSuggestions) {
        this.maxSuggestions = maxSuggestions;
    }

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public int getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(int cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }
}
