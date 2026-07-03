package io.muzoo.ssc.controlmap.report.document;

import java.util.List;

/**
 * A multi-section report document (the posture/auditor report, PLAN roadmap #3) — as opposed to the
 * single flat table of {@code ReportData}. The cover fields (title, subtitle, meta lines, headline)
 * render as the PDF's cover page and the CSV's leading block; {@code sections} then flow in order.
 *
 * <p>{@code metaLines} are the audit-provenance lines (generated at/by, scope, data as-of);
 * {@code classification} is the distribution notice ("Internal — …"). The {@code headline} is the
 * cover's score chip; {@code colorKey} is semantic (a band name) like everywhere in the model.
 */
public record ReportDocument(
        String title,
        String subtitle,
        List<String> metaLines,
        Headline headline,
        String classification,
        List<ReportSection> sections) {

    /** The cover's headline figure: the posture score and its band. */
    public record Headline(int score, String bandLabel, String colorKey) {
    }
}
