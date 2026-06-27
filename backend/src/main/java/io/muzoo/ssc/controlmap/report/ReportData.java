package io.muzoo.ssc.controlmap.report;

import java.util.List;

/**
 * Format-agnostic tabular report content: a title plus a header row and data rows (all stringified).
 * The {@link ReportFactory}'s writers turn this into a concrete format (CSV now, PDF later), so the
 * data-gathering layer never knows about the output format.
 */
public record ReportData(String title, List<String> headers, List<List<String>> rows) {
}
