package io.muzoo.ssc.controlmap.ai;

import static org.assertj.core.api.Assertions.assertThat;

import io.muzoo.ssc.controlmap.domain.Asset;
import io.muzoo.ssc.controlmap.domain.Control;
import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.domain.Role;
import io.muzoo.ssc.controlmap.domain.Severity;
import io.muzoo.ssc.controlmap.domain.User;
import io.muzoo.ssc.controlmap.repository.ControlRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * A <b>manual, live</b> smoke test / eval harness (PLAN §12): with the feature enabled and a real
 * {@code ANTHROPIC_API_KEY}, it makes an actual Claude call through the full wiring (AiConfig →
 * {@link SpringAiSuggestionModelClient} → {@link ClaudeCatalogStrategy}) against the real seeded
 * catalog, and prints the grounded suggestions. It is <b>skipped unless {@code -Dai.live=true}</b>,
 * so CI (no key, no spend) never runs it — the same guard pattern as the PDF-preview tests. Run:
 * {@code mvn test -Dtest=AiLiveSuggestionTest -Dai.live=true -Dsurefire.useFile=false} with the env
 * sourced ({@code ANTHROPIC_API_KEY} set and {@code AI_SUGGESTIONS_ENABLED} irrelevant — forced on here).
 */
@SpringBootTest(properties = "controlmap.ai.enabled=true")
@EnabledIfSystemProperty(named = "ai.live", matches = "true")
@Transactional // keep the Hibernate session open so the catalog's lazy Framework loads during prompt-building
class AiLiveSuggestionTest {

    @Autowired
    private MappingSuggestionStrategy strategy;

    @Autowired
    private ControlRepository controls;

    @Autowired
    private AiSuggestionProperties properties;

    @Test
    void suggestsGroundedControlsForASqlInjectionFinding() {
        List<Control> catalog = controls.findAll();
        assertThat(catalog).as("seeded catalog must be present").isNotEmpty();

        User owner = new User("live@smoke.test", "Live Smoke", "x", Role.ANALYST);
        Finding finding = new Finding("CM-LIVE-1", "SQL injection in login form",
                "User-supplied input is concatenated directly into a SQL query in the authentication "
                        + "endpoint, allowing authentication bypass and data exfiltration.",
                Severity.HIGH, null, owner,
                new Asset("customer-portal", "prod", "auth-service", "https://portal.example/login"));

        List<ControlSuggestion> suggestions = strategy.suggest(finding, catalog);

        System.out.println("\n=== LIVE Claude suggestions  (model=" + properties.getModel()
                + ", catalog=" + catalog.size() + " controls) ===");
        suggestions.forEach(s -> System.out.printf("  %-8s  conf=%.2f  %s — %s%n",
                s.control().getCode(), s.confidence(), s.control().getTitle(), s.rationale()));
        System.out.println("=== " + suggestions.size() + " grounded suggestion(s) ===\n");

        // Everything returned is a real catalog control (grounding held on a live response).
        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions).allSatisfy(s -> assertThat(catalog).contains(s.control()));
        assertThat(suggestions).allSatisfy(s -> assertThat(s.confidence()).isBetween(0.0, 1.0));
    }
}
