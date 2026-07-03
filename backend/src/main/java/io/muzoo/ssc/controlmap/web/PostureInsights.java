package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.domain.Severity;
import io.muzoo.ssc.controlmap.web.dto.FindingSummary;
import io.muzoo.ssc.controlmap.web.dto.PostureResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rule-based interpretation for the posture report — the "synthesis" layer. Every sentence is a pure,
 * deterministic function of the report's own data: reproducible (same data → same words), traceable
 * (each claim maps to a figure elsewhere in the report), and unit-testable. Deliberately NOT an LLM:
 * an audit artifact must not contain non-reproducible or unverifiable statements. The one time
 * dimension we have is finding age ({@code createdAt}); there is no snapshot history, so no rule may
 * make a trend claim ("improving"/"worsening") — the caveat states this limit explicitly.
 */
final class PostureInsights {

    /** Active statuses in wire form — {@link PostureService#ACTIVE} mapped once (single source). */
    private static final Set<String> ACTIVE_WIRE = PostureService.ACTIVE.stream()
            .map(FindingMapper::statusToWire)
            .collect(Collectors.toUnmodifiableSet());

    private PostureInsights() {
    }

    /** Per-framework coverage stats, precomputed by the assembler (report service). */
    record CoverageStat(String framework, int controls, long covered, long atRisk) {
        long gaps() {
            return controls - covered;
        }

        int coveredPct() {
            return controls == 0 ? 0 : Math.round(covered * 100f / controls);
        }
    }

    record Result(List<String> bullets, List<String> recommendations, String caveat) {
    }

    static Result compose(PostureResponse p, List<FindingSummary> all, List<CoverageStat> coverage, Instant now) {
        List<FindingSummary> active = all.stream().filter(f -> ACTIVE_WIRE.contains(f.status())).toList();
        List<String> bullets = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        driver(bullets, active, p);
        concentration(bullets, active);
        pipeline(bullets, p);
        aging(bullets, active, now);
        accepted(bullets, all);
        coverage(bullets, coverage);
        recommend(recommendations, p, all, coverage);

        String caveat = "Derived automatically from the data in this report by fixed rules - no manual "
                + "editing, no generative AI. Point-in-time snapshot: no score history exists yet, so "
                + "statements describe the present state only, never a trend.";
        return new Result(List.copyOf(bullets), List.copyOf(recommendations), caveat);
    }

    /** Which severity carries the posture load (weighted-sum shares, same weights as the score). */
    private static void driver(List<String> bullets, List<FindingSummary> active, PostureResponse p) {
        if (active.isEmpty()) {
            bullets.add("No active findings - the posture load is currently zero.");
            return;
        }
        int total = 0;
        Severity top = null;
        int topPoints = 0;
        for (Severity s : List.of(Severity.CRITICAL, Severity.HIGH, Severity.MEDIUM, Severity.LOW)) {
            int weight = PostureService.WEIGHTS.getOrDefault(s, 0);
            int points = (int) active.stream()
                    .filter(f -> f.severity().equals(FindingMapper.severityToWire(s))).count() * weight;
            total += points;
            if (points > topPoints) {           // largest contributor wins (ties keep the more severe band)
                top = s;
                topPoints = points;
            }
        }
        if (top != null && total > 0) {
            bullets.add(String.format(Locale.ROOT,
                    "%d of %d findings are active; %s findings carry the largest share of the posture "
                            + "load (%d of %d weighted points, %d%%).",
                    p.active(), p.total(), label(top).toLowerCase(Locale.ROOT), topPoints, total,
                    Math.round(topPoints * 100f / total)));
        }
    }

    /** Is the active risk concentrated in a few findings, or spread out? (Needs ≥ 4 to be meaningful.) */
    private static void concentration(List<String> bullets, List<FindingSummary> active) {
        List<BigDecimal> scores = active.stream()
                .map(FindingSummary::riskScore)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.reverseOrder())
                .toList();
        if (scores.size() < 4) {
            return;
        }
        double total = scores.stream().mapToDouble(BigDecimal::doubleValue).sum();
        double top3 = scores.stream().limit(3).mapToDouble(BigDecimal::doubleValue).sum();
        if (total <= 0) {
            return;
        }
        int pct = (int) Math.round(top3 * 100 / total);
        if (pct >= 60) {
            bullets.add("Risk is concentrated: the top 3 findings carry " + pct
                    + "% of the total active risk score - remediating a few items moves the posture most.");
        } else {
            bullets.add("Active risk is spread across " + scores.size()
                    + " findings (the top 3 carry " + pct + "%) - broad remediation effort is needed.");
        }
    }

    /** Review-workflow signals: items awaiting decision, items returned for rework. */
    private static void pipeline(List<String> bullets, PostureResponse p) {
        long submitted = statusCount(p, "submitted");
        long returned = statusCount(p, "returned");
        if (submitted > 0) {
            bullets.add(submitted + " finding" + plural(submitted) + " await" + (submitted == 1 ? "s" : "")
                    + " reviewer decision in the review queue.");
        }
        if (returned > 0) {
            bullets.add(returned + " finding" + plural(returned) + " " + (returned == 1 ? "was" : "were")
                    + " returned by review and need" + (returned == 1 ? "s" : "") + " analyst rework.");
        }
    }

    /** The longest-open active high/critical finding, by creation date (the one time axis we have). */
    private static void aging(List<String> bullets, List<FindingSummary> active, Instant now) {
        active.stream()
                .filter(f -> ("critical".equals(f.severity()) || "high".equals(f.severity())) && f.createdAt() != null)
                .min(Comparator.comparing(FindingSummary::createdAt))
                .ifPresent(oldest -> {
                    long days = Duration.between(oldest.createdAt(), now).toDays();
                    bullets.add("The longest-open high/critical finding (" + oldest.reference()
                            + ") has been active for " + days + " day" + plural(days) + ".");
                });
    }

    /** Standing accepted exposure — what someone signed off on retaining. */
    private static void accepted(List<String> bullets, List<FindingSummary> all) {
        List<FindingSummary> acceptedAll = all.stream().filter(f -> "accepted".equals(f.status())).toList();
        if (acceptedAll.isEmpty()) {
            return;
        }
        long hot = acceptedAll.stream()
                .filter(f -> "critical".equals(f.severity()) || "high".equals(f.severity())).count();
        bullets.add(acceptedAll.size() + " risk" + plural(acceptedAll.size()) + " formally accepted"
                + (hot > 0 ? ", including " + hot + " high/critical" : "")
                + " - exposure retained by explicit decision (see Risk acceptances).");
    }

    /** Coverage interpretation — honest framing: a gap means unassessed OR unaffected, never "compliant". */
    private static void coverage(List<String> bullets, List<CoverageStat> coverage) {
        if (coverage.isEmpty()) {
            return;
        }
        CoverageStat weakest = coverage.stream()
                .min(Comparator.comparingInt(CoverageStat::coveredPct))
                .orElseThrow();
        if (weakest.gaps() > 0) {
            bullets.add(weakest.framework() + " has the lowest control coverage (" + weakest.coveredPct()
                    + "% - " + weakest.gaps() + " control" + plural(weakest.gaps())
                    + " with no mapped findings; unmapped controls are either unaffected or not yet assessed).");
        }
        long atRisk = coverage.stream().mapToLong(CoverageStat::atRisk).sum();
        if (atRisk > 0) {
            bullets.add(atRisk + " control" + plural(atRisk) + " across all frameworks "
                    + (atRisk == 1 ? "is" : "are")
                    + " currently at risk (mapped to at least one active high/critical finding).");
        }
    }

    /** Prioritized, formulaic action list — only applicable items appear, in fixed priority order. */
    private static void recommend(List<String> recommendations, PostureResponse p, List<FindingSummary> all,
                                  List<CoverageStat> coverage) {
        if (p.criticalActive() > 0) {
            recommendations.add("Remediate the " + p.criticalActive() + " active critical finding"
                    + plural(p.criticalActive()) + " first - critical findings carry the largest posture weight.");
        }
        long submitted = statusCount(p, "submitted");
        if (submitted > 0) {
            recommendations.add("Clear the review queue: " + submitted + " submitted finding"
                    + plural(submitted) + " await" + (submitted == 1 ? "s" : "") + " a reviewer decision.");
        }
        long returned = statusCount(p, "returned");
        if (returned > 0) {
            recommendations.add("Rework and resubmit the " + returned + " returned finding" + plural(returned) + ".");
        }
        long acceptedHot = all.stream()
                .filter(f -> "accepted".equals(f.status()))
                .filter(f -> "critical".equals(f.severity()) || "high".equals(f.severity())).count();
        if (acceptedHot > 0) {
            recommendations.add("Re-validate the " + acceptedHot + " accepted high/critical risk"
                    + plural(acceptedHot) + " - confirm the acceptance rationale still holds.");
        }
        coverage.stream()
                .filter(c -> c.gaps() > 0)
                .min(Comparator.comparingInt(CoverageStat::coveredPct))
                .ifPresent(weakest -> recommendations.add("Assess the " + weakest.gaps() + " unmapped control"
                        + plural(weakest.gaps()) + " in " + weakest.framework() + " to close assessment gaps."));
        if (recommendations.isEmpty()) {
            recommendations.add("No immediate actions required - maintain the current review cadence.");
        }
    }

    // ---- small helpers ----

    private static long statusCount(PostureResponse p, String key) {
        return p.byStatus().stream()
                .filter(s -> key.equals(s.key()))
                .mapToLong(PostureResponse.StatusCount::count)
                .findFirst()
                .orElse(0);
    }

    private static String label(Severity s) {
        String name = s.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String plural(long n) {
        return n == 1 ? "" : "s";
    }
}
