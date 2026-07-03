package io.muzoo.ssc.controlmap.report;

import java.util.List;

/**
 * Format-agnostic report content: a title, an optional summary line, provenance meta lines (generated
 * when / by whom + classification), the columns (header + a relative width weight), and the
 * stringified data rows. The {@link ReportFactory}'s writers turn this into a concrete format — the
 * PDF renders the meta block and uses the weights; CSV ignores both and stays a clean header+rows
 * table — so the data-gathering layer never knows about the output format.
 */
public record ReportData(String title, String subtitle, List<String> metaLines,
                         List<Column> columns, List<List<String>> rows) {

    /** A column: its header text and a relative width weight (used only by table formats like PDF). */
    public record Column(String header, float weight) {
    }

    /** The plain header strings, for formats that don't weight columns (CSV). */
    public List<String> headers() {
        return columns.stream().map(Column::header).toList();
    }
}
