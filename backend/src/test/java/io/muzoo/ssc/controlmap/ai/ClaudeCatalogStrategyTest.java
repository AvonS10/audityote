package io.muzoo.ssc.controlmap.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.muzoo.ssc.controlmap.domain.Asset;
import io.muzoo.ssc.controlmap.domain.Control;
import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.domain.Framework;
import io.muzoo.ssc.controlmap.domain.Role;
import io.muzoo.ssc.controlmap.domain.Severity;
import io.muzoo.ssc.controlmap.domain.User;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Grounding + parsing for the Claude control-mapping strategy (PLAN §4/§12/§14 S1). Pure unit test:
 * the {@link SuggestionModelClient} is a fake returning canned JSON, so there is no live API call and
 * no spend — the point is to prove the strategy parses the model output and <b>drops any control code
 * the model invents</b>, keeping only real catalog entries.
 */
class ClaudeCatalogStrategyTest {

    private static final Framework ISO = new Framework("iso27001", "ISO/IEC 27001", "2022");
    private static final Framework OWASP = new Framework("owasp2025", "OWASP Top 10", "2025");
    private static final Framework NIST = new Framework("nistcsf", "NIST CSF", "2.0");

    private static final Control SECURE_CODING =
            new Control(ISO, "A.8.28", "Secure coding", "Apply secure coding principles.", "Technological");
    private static final Control INJECTION =
            new Control(OWASP, "A03", "Injection", "Injection flaws.", null);
    private static final Control ACCESS_CONTROL =
            new Control(NIST, "PR.AA", "Identity Management and Access Control", "Manage access.", "Protect");

    private static final List<Control> CATALOG = List.of(SECURE_CODING, INJECTION, ACCESS_CONTROL);

    private static final User OWNER = new User("o@ai.test", "Owner", "x", Role.ANALYST);

    private static Finding finding() {
        return new Finding("CM-AI-1", "SQL injection in login form",
                "User-supplied input is concatenated into a SQL query.", Severity.HIGH, null, OWNER,
                new Asset("web-app", "prod", "auth", "https://app.example/login"));
    }

    private ClaudeCatalogStrategy strategyReturning(String cannedResponse) {
        SuggestionModelClient fake = (system, user) -> cannedResponse;
        return new ClaudeCatalogStrategy(fake, new AiSuggestionProperties());
    }

    @Test
    void keepsValidCodeAndDropsHallucinatedOne() {
        // One real catalog code (A.8.28) and one the model invented (ZZ.999) — the invented one is dropped.
        String response = """
                [
                  {"code": "A.8.28", "confidence": 0.92, "rationale": "Secure coding prevents injection."},
                  {"code": "ZZ.999", "confidence": 0.88, "rationale": "This control does not exist."}
                ]
                """;

        List<ControlSuggestion> result = strategyReturning(response).suggest(finding(), CATALOG);

        assertThat(result).hasSize(1);
        ControlSuggestion only = result.get(0);
        assertThat(only.control().getCode()).isEqualTo("A.8.28");
        assertThat(only.confidence()).isEqualTo(0.92);
        assertThat(only.rationale()).isEqualTo("Secure coding prevents injection.");
    }

    @Test
    void parsesResponseWrappedInMarkdownFences() {
        String response = "```json\n[{\"code\":\"A03\",\"confidence\":0.7,\"rationale\":\"Injection category.\"}]\n```";

        List<ControlSuggestion> result = strategyReturning(response).suggest(finding(), CATALOG);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).control().getCode()).isEqualTo("A03");
    }

    @Test
    void toleratesObjectWrappingTheArray() {
        // Some models wrap the array in an object; the first array field is used.
        String response = "{\"suggestions\":[{\"code\":\"PR.AA\",\"confidence\":0.6,\"rationale\":\"Access control.\"}]}";

        List<ControlSuggestion> result = strategyReturning(response).suggest(finding(), CATALOG);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).control().getCode()).isEqualTo("PR.AA");
    }

    @Test
    void emptyArrayYieldsNoSuggestions() {
        assertThat(strategyReturning("[]").suggest(finding(), CATALOG)).isEmpty();
    }

    @Test
    void malformedResponseThrowsSuggestionException() {
        assertThatThrownBy(() -> strategyReturning("Sorry, I can't help with that.").suggest(finding(), CATALOG))
                .isInstanceOf(MappingSuggestionException.class);
    }

    @Test
    void sortsByConfidenceDescendingAndCapsToMaxSuggestions() {
        AiSuggestionProperties props = new AiSuggestionProperties();
        props.setMaxSuggestions(2);
        String response = """
                [
                  {"code": "A.8.28", "confidence": 0.50, "rationale": "a"},
                  {"code": "A03",    "confidence": 0.90, "rationale": "b"},
                  {"code": "PR.AA",  "confidence": 0.70, "rationale": "c"}
                ]
                """;

        List<ControlSuggestion> result =
                new ClaudeCatalogStrategy((s, u) -> response, props).suggest(finding(), CATALOG);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(cs -> cs.control().getCode()).containsExactly("A03", "PR.AA");
    }

    @Test
    void groundsCaseInsensitivelyAndClampsConfidenceToRange() {
        // Model echoes a lowercased code and out-of-range confidences; both are handled.
        String response = """
                [
                  {"code": "a.8.28", "confidence": 1.7, "rationale": "over"},
                  {"code": "pr.aa",  "confidence": -0.4, "rationale": "under"}
                ]
                """;

        List<ControlSuggestion> result = strategyReturning(response).suggest(finding(), CATALOG);

        assertThat(result).extracting(cs -> cs.control().getCode()).containsExactlyInAnyOrder("A.8.28", "PR.AA");
        assertThat(result).allSatisfy(cs -> assertThat(cs.confidence()).isBetween(0.0, 1.0));
    }

    @Test
    void dropsDuplicateSuggestionsOfTheSameControl() {
        String response = """
                [
                  {"code": "A03", "confidence": 0.9, "rationale": "first"},
                  {"code": "A03", "confidence": 0.8, "rationale": "second"}
                ]
                """;

        List<ControlSuggestion> result = strategyReturning(response).suggest(finding(), CATALOG);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).rationale()).isEqualTo("first");
    }

    @Test
    void emptyCatalogShortCircuitsWithoutCallingTheModel() {
        SuggestionModelClient exploding = (system, user) -> {
            throw new AssertionError("model must not be called when the catalog is empty");
        };
        ClaudeCatalogStrategy strategy = new ClaudeCatalogStrategy(exploding, new AiSuggestionProperties());

        assertThat(strategy.suggest(finding(), List.of())).isEmpty();
    }
}
