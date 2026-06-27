package io.muzoo.ssc.controlmap.report;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * RFC 4180 CSV writer: CRLF line endings, fields containing a comma, quote or newline are wrapped in
 * double quotes with embedded quotes doubled. Defensive quoting also neutralises CSV "formula
 * injection" (a field starting with = + - @) by quoting it, so spreadsheets treat it as text.
 */
@Component
public class CsvReportWriter implements ReportWriter {

    @Override
    public ReportFormat format() {
        return ReportFormat.CSV;
    }

    @Override
    public byte[] write(ReportData data) {
        StringBuilder out = new StringBuilder();
        appendRow(out, data.headers());
        for (List<String> row : data.rows()) {
            appendRow(out, row);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendRow(StringBuilder out, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(escape(fields.get(i)));
        }
        out.append("\r\n");
    }

    private static String escape(String value) {
        String v = value == null ? "" : value;
        boolean needsQuote = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")
                || (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0);
        if (needsQuote) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }
}
