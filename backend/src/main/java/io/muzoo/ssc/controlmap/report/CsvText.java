package io.muzoo.ssc.controlmap.report;

import java.util.List;

/**
 * Shared CSV primitives — RFC 4180 escaping (with the formula-injection guard), row emission, and the
 * UTF-8 BOM. Extracted from {@code CsvReportWriter} so the document writer (posture report) emits CSV
 * with the same quoting and encoding policy (DRY).
 */
public final class CsvText {

    /** UTF-8 byte-order mark — makes Excel &amp; co. read the file as UTF-8 instead of the system locale. */
    public static final char BOM = (char) 0xFEFF; // U+FEFF

    private CsvText() {
    }

    public static void appendRow(StringBuilder out, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(escape(fields.get(i)));
        }
        out.append("\r\n");
    }

    /**
     * RFC 4180 escaping: fields containing a comma, quote or newline are wrapped in double quotes with
     * embedded quotes doubled. Defensive quoting also neutralises CSV "formula injection" (a field
     * starting with = + - @) by quoting it, so spreadsheets treat it as text.
     */
    public static String escape(String value) {
        String v = value == null ? "" : value;
        boolean needsQuote = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")
                || (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0);
        if (needsQuote) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }
}
