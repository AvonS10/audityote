package io.muzoo.ssc.controlmap.report.document;

import io.muzoo.ssc.controlmap.report.ReportData;
import java.util.List;

/**
 * One block of a {@link ReportDocument} (Composite, PLAN §11): the posture report is a heterogeneous
 * sequence of sections — KPI tiles, a gauge, charts, tables, prose — that a {@link DocumentWriter}
 * renders uniformly. The interface is <em>sealed</em> so writers can pattern-match exhaustively: adding
 * a section type fails compilation until every writer handles it (no silently-dropped content).
 *
 * <p>Sections carry data plus <em>semantic</em> color keys (severity/status/band names — e.g.
 * {@code critical}, {@code submitted}); only the PDF writer resolves keys to actual colors (mirroring
 * the design tokens), and the CSV writer ignores them. Presentation never leaks into the model.
 */
public sealed interface ReportSection
        permits ReportSection.Kpis, ReportSection.Gauge, ReportSection.BarChart, ReportSection.Heatmap,
                ReportSection.TableSection, ReportSection.Insights, ReportSection.Paragraphs {

    /** Headline stat tiles (mirrors the posture screen's StatCards). */
    record Kpis(List<Kpi> kpis) implements ReportSection {
        public record Kpi(String label, String value, String caption) {
        }
    }

    /**
     * The 0–100 posture gauge plus its band scale. {@code bands} are the policy thresholds in display
     * order; the current band is the first whose {@code max} exceeds the score (same rule as the SPA).
     */
    record Gauge(String title, int score, String bandLabel, List<Band> bands) implements ReportSection {
        public record Band(String label, int max, String colorKey) {
        }
    }

    /** A horizontal bar breakdown — every bar is direct-labelled (identity never rides on hue alone). */
    record BarChart(String title, String note, List<Bar> bars) implements ReportSection {
        public record Bar(String label, long count, String colorKey) {
        }
    }

    /**
     * The severity×status cross-tab. {@code intensityPct} is the cell's <em>inherent-risk</em> tint
     * (severity weight × workflow-state factor, same formula as the screen) — display policy computed
     * by the assembler so both writers stay dumb; CSV renders counts only.
     */
    record Heatmap(String title, String note, List<String> colLabels, List<Row> rows) implements ReportSection {
        public record Row(String label, String colorKey, List<Cell> cells) {
        }

        public record Cell(long count, int intensityPct) {
        }
    }

    /** A titled table; reuses {@link ReportData.Column} (header + width weight) from the flat model. */
    record TableSection(String title, String note, List<ReportData.Column> columns, List<List<String>> rows)
            implements ReportSection {
    }

    /**
     * Rule-derived interpretation: observation bullets + prioritized recommendations. Deterministic —
     * every statement is a pure function of the data elsewhere in the report (reproducible, traceable);
     * the {@code caveat} labels it as derived and states what the data cannot support (e.g. trends).
     */
    record Insights(String title, List<String> bullets, List<String> recommendations, String caveat)
            implements ReportSection {
    }

    /** Titled prose paragraphs (methodology, definitions). */
    record Paragraphs(String title, List<String> lines) implements ReportSection {
    }
}
