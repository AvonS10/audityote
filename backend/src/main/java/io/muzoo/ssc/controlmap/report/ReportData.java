package io.muzoo.ssc.controlmap.report;

import java.util.List;

/**
 * Format-agnostic report content: a title, an optional summary line, the columns (header + a relative
 * width weight), and the stringified data rows. The {@link ReportFactory}'s writers turn this into a
 * concrete format — CSV ignores the weights/summary; the PDF table uses them — so the data-gathering
 * layer never knows about the output format.
 */
public record ReportData(String title, String subtitle, List<Column> columns, List<List<String>> rows) {

    /** A column: its header text and a relative width weight (used only by table formats like PDF). */
    public record Column(String header, float weight) {
    }

    /** The plain header strings, for formats that don't weight columns (CSV). */
    public List<String> headers() {
        return columns.stream().map(Column::header).toList();
    }
}
