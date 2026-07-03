package io.muzoo.ssc.controlmap.report;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * RFC 4180 CSV writer: CRLF line endings, defensive quoting (incl. the formula-injection guard) and a
 * leading UTF-8 BOM so spreadsheets detect the encoding — the actual escaping lives in {@link CsvText},
 * shared with the document writer.
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
        out.append(CsvText.BOM);
        CsvText.appendRow(out, data.headers());
        for (List<String> row : data.rows()) {
            CsvText.appendRow(out, row);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
}
