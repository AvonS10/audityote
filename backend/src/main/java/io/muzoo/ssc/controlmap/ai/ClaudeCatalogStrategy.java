package io.muzoo.ssc.controlmap.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.muzoo.ssc.controlmap.domain.Asset;
import io.muzoo.ssc.controlmap.domain.Control;
import io.muzoo.ssc.controlmap.domain.Finding;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The default {@link MappingSuggestionStrategy} (PLAN §4): Claude-only, grounded to the seeded catalog.
 * It builds a prompt from the finding plus the catalog, asks the model (via the {@link
 * SuggestionModelClient} port) for a JSON array of {@code {code, confidence, rationale}}, then
 * <b>grounds</b> the result — every returned code is matched back to a real catalog {@link Control} and
 * any code the model invented is dropped. This grounding is the whole point of "catalog-grounded" and
 * is what the S1 tests exercise with a fake client (no live API — PLAN §12).
 *
 * <p>Parsing is deliberately owned here (rather than delegated to a library's structured-output binding)
 * so the parse + grounding logic is fully unit-testable without loading Spring or Spring AI.
 */
public class ClaudeCatalogStrategy implements MappingSuggestionStrategy {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_RATIONALE_CHARS = 280;

    private final SuggestionModelClient client;
    private final AiSuggestionProperties properties;

    public ClaudeCatalogStrategy(SuggestionModelClient client, AiSuggestionProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public List<ControlSuggestion> suggest(Finding finding, List<Control> catalog) {
        if (catalog == null || catalog.isEmpty()) {
            return List.of(); // Nothing to ground against — no suggestions possible.
        }

        String raw = client.complete(systemPrompt(catalog), userPrompt(finding));
        JsonNode array = parseArray(raw);
        return ground(array, catalog);
    }

    /**
     * The stable, cacheable prefix: the instruction/contract plus the whole control catalog to ground
     * against. It is identical for every finding, so the SDK client marks it {@code cache_control:
     * ephemeral} and Anthropic reuses it across calls (C2). Keep this byte-identical across calls — the
     * catalog is fetched in a stable order for exactly that reason.
     */
    private String systemPrompt(List<Control> catalog) {
        StringBuilder sb = new StringBuilder("""
                You are a GRC (governance, risk and compliance) assistant that maps security findings to \
                security controls. You are given ONE finding and a CATALOG of controls, each with a unique \
                code. Choose the controls from the catalog that most directly mitigate or detect the finding.

                Rules:
                - Only use control codes that appear verbatim in the catalog. Never invent a code.
                - Return at most %d suggestions, best match first.
                - Respond with ONLY a JSON array, no prose and no markdown code fences. Each element is:
                  {"code": "<catalog code>", "confidence": <number 0.0-1.0>, "rationale": "<one concise sentence>"}
                - If nothing in the catalog is relevant, return [].

                CATALOG (code — framework — title (category): description)
                """.formatted(properties.getMaxSuggestions()));
        for (Control c : catalog) {
            sb.append(c.getCode()).append(" — ")
                    .append(c.getFramework().getName()).append(' ').append(c.getFramework().getVersion())
                    .append(" — ").append(c.getTitle());
            if (c.getCategory() != null && !c.getCategory().isBlank()) {
                sb.append(" (").append(c.getCategory()).append(')');
            }
            // The description gives the model each control's intent (better grounding) and enlarges the
            // stable prefix past Haiku's 4096-token cache minimum, so prompt caching actually engages (C2).
            if (c.getDescription() != null && !c.getDescription().isBlank()) {
                sb.append(": ").append(c.getDescription());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Describes the finding (full detail — §12 responsible-AI choice); the varying part of the prompt. */
    private String userPrompt(Finding finding) {
        StringBuilder sb = new StringBuilder();
        sb.append("FINDING\n");
        sb.append("Title: ").append(nullToEmpty(finding.getTitle())).append('\n');
        sb.append("Severity: ").append(finding.getSeverity().name().toLowerCase(Locale.ROOT)).append('\n');
        sb.append("Asset: ").append(describeAsset(finding.getAsset())).append('\n');
        sb.append("Description:\n").append(nullToEmpty(finding.getDescription()));
        return sb.toString();
    }

    private static String describeAsset(Asset asset) {
        if (asset == null) {
            return "(unspecified)";
        }
        StringBuilder sb = new StringBuilder(nullToEmpty(asset.getName()));
        List<String> extra = new ArrayList<>();
        if (asset.getEnv() != null && !asset.getEnv().isBlank()) {
            extra.add("env: " + asset.getEnv());
        }
        if (asset.getComponent() != null && !asset.getComponent().isBlank()) {
            extra.add("component: " + asset.getComponent());
        }
        if (asset.getUrl() != null && !asset.getUrl().isBlank()) {
            extra.add("url: " + asset.getUrl());
        }
        if (!extra.isEmpty()) {
            sb.append(" (").append(String.join(", ", extra)).append(')');
        }
        return sb.toString();
    }

    /** Parses the model text into a JSON array node, tolerating markdown fences and a wrapping object. */
    private JsonNode parseArray(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new MappingSuggestionException("The suggestion model returned an empty response.");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(stripFences(raw));
        } catch (Exception e) {
            throw new MappingSuggestionException("The suggestion model returned unparseable JSON.", e);
        }
        if (root.isArray()) {
            return root;
        }
        // Tolerate a wrapping object, e.g. {"suggestions": [...]}: use its first array field.
        if (root.isObject()) {
            for (JsonNode child : root) {
                if (child.isArray()) {
                    return child;
                }
            }
        }
        throw new MappingSuggestionException("The suggestion model response was not a JSON array.");
    }

    /**
     * Grounds the raw suggestions against the catalog: keep only codes that exist (case-insensitively),
     * resolve each to its {@link Control}, drop hallucinated codes and duplicates, clamp confidence, then
     * sort best-first and cap at {@code maxSuggestions}.
     */
    private List<ControlSuggestion> ground(JsonNode array, List<Control> catalog) {
        Map<String, Control> byCode = new LinkedHashMap<>();
        for (Control c : catalog) {
            byCode.put(normalise(c.getCode()), c);
        }

        List<ControlSuggestion> grounded = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();
        for (JsonNode node : array) {
            String code = node.path("code").asText(null);
            if (code == null || code.isBlank()) {
                continue;
            }
            String key = normalise(code);
            Control control = byCode.get(key);
            if (control == null || !seenCodes.add(key)) {
                continue; // Hallucinated code, or the same control suggested twice — drop it.
            }
            double confidence = clamp(node.path("confidence").asDouble(0.5));
            grounded.add(new ControlSuggestion(control, confidence, rationale(node)));
        }

        grounded.sort(Comparator.comparingDouble(ControlSuggestion::confidence).reversed());
        int cap = Math.max(0, properties.getMaxSuggestions());
        return grounded.size() > cap ? new ArrayList<>(grounded.subList(0, cap)) : grounded;
    }

    private static String rationale(JsonNode node) {
        String text = node.path("rationale").asText("").trim();
        return text.length() > MAX_RATIONALE_CHARS ? text.substring(0, MAX_RATIONALE_CHARS) : text;
    }

    /** Uppercases and trims a control code so grounding tolerates the model echoing a different case. */
    private static String normalise(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private static double clamp(double v) {
        return v < 0.0 ? 0.0 : Math.min(v, 1.0);
    }

    /** Strips a leading/trailing ```json ... ``` fence if the model wrapped its JSON in one. */
    private static String stripFences(String raw) {
        String s = raw.strip();
        if (!s.startsWith("```")) {
            return s;
        }
        // Drop the opening ``` plus an optional language tag and any following whitespace (covers both
        // ```json\n[...] and a single-line ```json[...] with no newline), then the trailing ```.
        s = s.replaceFirst("^```[a-zA-Z0-9]*\\s*", "");
        int lastFence = s.lastIndexOf("```");
        if (lastFence >= 0) {
            s = s.substring(0, lastFence);
        }
        return s.strip();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
