package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.ai.AiSuggestionProperties;
import io.muzoo.ssc.controlmap.web.dto.ConfigResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes client-visible server capabilities (PLAN §7.12) — currently just whether AI control
 * suggestions are enabled, so the SPA can conditionally show the "Suggest controls" affordance.
 * Authenticated (any signed-in user); returns only the on/off flag, never the API key or model.
 */
@RestController
@RequestMapping("/api")
public class ConfigController {

    private final AiSuggestionProperties aiProperties;

    public ConfigController(AiSuggestionProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @GetMapping("/config")
    public ConfigResponse config() {
        return new ConfigResponse(aiProperties.isEnabled());
    }
}
