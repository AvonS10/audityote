package io.muzoo.ssc.controlmap.report.document;

import io.muzoo.ssc.controlmap.report.CsvText;
import io.muzoo.ssc.controlmap.report.ReportData.Column;
import io.muzoo.ssc.controlmap.report.ReportFormat;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The raw-numbers companion to the PDF: renders a {@link ReportDocument} as blank-line-separated CSV
 * blocks — every figure behind the charts, none of the drawing. Charts serialize as their underlying
 * counts (a bar chart → label,count rows; the heatmap → a severity×status cross-tab); color keys and
 * tint intensities are presentation-only and are ignored here. Same RFC 4180 escaping + UTF-8 BOM as
 * the tabular CSV writer (via {@link CsvText}).
 */
@Component
public class CsvDocumentWriter implements DocumentWriter {

    @Override
    public ReportFormat format() {
        return ReportFormat.CSV;
    }

    @Override
    public byte[] write(ReportDocument document) {
        StringBuilder out = new StringBuilder();
        out.append(CsvText.BOM);
        row(out, document.title());
        row(out, document.subtitle());
        for (String meta : document.metaLines()) {
            row(out, meta);
        }
        row(out, document.classification());
        blank(out);
        row(out, "Posture score", String.valueOf(document.headline().score()));
        row(out, "Posture band", document.headline().bandLabel());

        for (ReportSection section : document.sections()) {
            blank(out);
            switch (section) {
                case ReportSection.Kpis s -> {
                    for (ReportSection.Kpis.Kpi kpi : s.kpis()) {
                        row(out, kpi.label(), kpi.value(), kpi.caption());
                    }
                }
                case ReportSection.Gauge s -> {
                    row(out, s.title());
                    row(out, "Band", "Score range");
                    int prev = 0;
                    for (ReportSection.Gauge.Band band : s.bands()) {
                        row(out, band.label(), prev + "-" + (Math.min(band.max(), 100) - 1));
                        prev = Math.min(band.max(), 100);
                    }
                }
                case ReportSection.BarChart s -> {
                    row(out, s.title());
                    row(out, "Label", "Count");
                    for (ReportSection.BarChart.Bar bar : s.bars()) {
                        row(out, bar.label(), String.valueOf(bar.count()));
                    }
                }
                case ReportSection.Heatmap s -> {
                    row(out, s.title());
                    List<String> header = new ArrayList<>();
                    header.add("Severity");
                    header.addAll(s.colLabels());
                    CsvText.appendRow(out, header);
                    for (ReportSection.Heatmap.Row hr : s.rows()) {
                        List<String> cells = new ArrayList<>();
                        cells.add(hr.label());
                        for (ReportSection.Heatmap.Cell cell : hr.cells()) {
                            cells.add(String.valueOf(cell.count()));
                        }
                        CsvText.appendRow(out, cells);
                    }
                }
                case ReportSection.TableSection s -> {
                    row(out, s.title());
                    if (s.note() != null && !s.note().isBlank()) {
                        row(out, s.note());
                    }
                    CsvText.appendRow(out, s.columns().stream().map(Column::header).toList());
                    for (List<String> r : s.rows()) {
                        CsvText.appendRow(out, r);
                    }
                }
                case ReportSection.Insights s -> {
                    row(out, s.title());
                    for (String bullet : s.bullets()) {
                        row(out, bullet);
                    }
                    row(out, "Recommended actions");
                    int n = 1;
                    for (String rec : s.recommendations()) {
                        row(out, n++ + ". " + rec);
                    }
                    row(out, s.caveat());
                }
                case ReportSection.Paragraphs s -> {
                    row(out, s.title());
                    for (String line : s.lines()) {
                        row(out, line);
                    }
                }
            }
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void row(StringBuilder out, String... fields) {
        CsvText.appendRow(out, List.of(fields));
    }

    private static void blank(StringBuilder out) {
        out.append("\r\n");
    }
}
