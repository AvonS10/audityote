package io.muzoo.ssc.controlmap.report;

import io.muzoo.ssc.controlmap.report.ReportData.Column;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

/**
 * Renders {@link ReportData} as a human-readable, printable A4-landscape PDF: a titled report with a
 * summary line and a provenance meta block (generated when / by whom + classification), then a table
 * whose columns are sized by each column's weight and whose long cells <em>word-wrap</em> onto
 * multiple lines (variable row height) — nothing is truncated. The header row repeats on every page,
 * and a second pass stamps the shared brand + page-number footer ({@link PdfFooter}). Same input as
 * {@link CsvReportWriter}: gather once, format many. PDF is the presentation artifact (hand to an
 * auditor); CSV stays the full machine-readable dump.
 */
@Component
public class PdfReportWriter implements ReportWriter {

    private static final PDFont TITLE_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont HEADER_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont BODY_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    private static final float MARGIN = 36f;
    private static final float TITLE_SIZE = 15f;
    private static final float SUBTITLE_SIZE = 9f;
    private static final float META_SIZE = 7.5f;
    private static final float HEADER_SIZE = 8.5f;
    private static final float BODY_SIZE = 8f;
    private static final float CELL_PAD_X = 4f;
    private static final float CELL_PAD_Y = 4f;
    private static final float LINE_GAP = 2.6f; // extra leading between wrapped lines

    private static final Color TEXT = new Color(0.12f, 0.12f, 0.12f);
    private static final Color MUTED = new Color(0.42f, 0.42f, 0.40f);
    private static final Color HEADER_FILL = new Color(0.93f, 0.93f, 0.91f);
    private static final Color RULE = new Color(0.80f, 0.80f, 0.78f);

    @Override
    public ReportFormat format() {
        return ReportFormat.PDF;
    }

    @Override
    public byte[] write(ReportData data) {
        PDRectangle pageSize = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()); // landscape
        float usable = pageSize.getWidth() - 2 * MARGIN;
        float[] colX = new float[data.columns().size()];
        float[] colW = new float[data.columns().size()];
        layoutColumns(data.columns(), usable, colX, colW);
        List<String> headers = data.headers();

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(pageSize);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);

            float y = pageSize.getHeight() - MARGIN;
            text(cs, TITLE_FONT, TITLE_SIZE, TEXT, MARGIN, y - TITLE_SIZE, PdfText.sanitize(data.title()));
            y -= TITLE_SIZE + 7;
            if (data.subtitle() != null && !data.subtitle().isBlank()) {
                text(cs, BODY_FONT, SUBTITLE_SIZE, MUTED, MARGIN, y - SUBTITLE_SIZE, PdfText.sanitize(data.subtitle()));
                y -= SUBTITLE_SIZE + (data.metaLines().isEmpty() ? 11 : 7);
            } else {
                y -= 5;
            }
            for (String line : data.metaLines()) {
                text(cs, BODY_FONT, META_SIZE, MUTED, MARGIN, y - META_SIZE, PdfText.sanitize(line));
                y -= META_SIZE + 3.5f;
            }
            if (!data.metaLines().isEmpty()) {
                y -= 7;
            }
            y = drawHeader(cs, headers, colX, colW, usable, y);

            for (List<String> row : data.rows()) {
                List<List<String>> wrapped = wrapRow(row, colW, BODY_FONT, BODY_SIZE);
                float h = rowHeight(wrapped, BODY_SIZE);
                if (y - h < MARGIN) {
                    cs.close();
                    page = new PDPage(pageSize);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = drawHeader(cs, headers, colX, colW, usable, pageSize.getHeight() - MARGIN);
                }
                paint(cs, wrapped, colX, y, BODY_FONT, BODY_SIZE, TEXT);
                y -= h;
                rule(cs, MARGIN, y, MARGIN + usable, y);
            }
            cs.close();
            PdfFooter.apply(doc, data.title(), MARGIN, usable);
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render PDF report", e);
        }
    }

    private static void layoutColumns(List<Column> columns, float usable, float[] colX, float[] colW) {
        float sum = 0f;
        for (Column c : columns) {
            sum += c.weight();
        }
        if (sum <= 0f) {
            sum = Math.max(1, columns.size());
        }
        float x = MARGIN;
        for (int i = 0; i < columns.size(); i++) {
            colW[i] = usable * columns.get(i).weight() / sum;
            colX[i] = x;
            x += colW[i];
        }
    }

    /** Draws the header row (light fill + bold text + underline) and returns the y below it. */
    private float drawHeader(PDPageContentStream cs, List<String> headers, float[] colX, float[] colW, float usable, float topY)
            throws IOException {
        List<List<String>> wrapped = wrapRow(headers, colW, HEADER_FONT, HEADER_SIZE);
        float h = rowHeight(wrapped, HEADER_SIZE);
        cs.setNonStrokingColor(HEADER_FILL);
        cs.addRect(MARGIN, topY - h, usable, h);
        cs.fill();
        paint(cs, wrapped, colX, topY, HEADER_FONT, HEADER_SIZE, TEXT);
        rule(cs, MARGIN, topY - h, MARGIN + usable, topY - h);
        return topY - h;
    }

    private List<List<String>> wrapRow(List<String> cells, float[] colW, PDFont font, float size) throws IOException {
        List<List<String>> wrapped = new ArrayList<>(cells.size());
        for (int i = 0; i < cells.size(); i++) {
            wrapped.add(PdfText.wrap(PdfText.sanitize(cells.get(i)), font, size, colW[i] - 2 * CELL_PAD_X));
        }
        return wrapped;
    }

    private static float rowHeight(List<List<String>> wrapped, float size) {
        int maxLines = 1;
        for (List<String> lines : wrapped) {
            maxLines = Math.max(maxLines, lines.size());
        }
        return maxLines * (size + LINE_GAP) + 2 * CELL_PAD_Y;
    }

    private static void paint(PDPageContentStream cs, List<List<String>> wrapped, float[] colX, float topY,
                              PDFont font, float size, Color color) throws IOException {
        float lineHeight = size + LINE_GAP;
        for (int i = 0; i < wrapped.size(); i++) {
            float x = colX[i] + CELL_PAD_X;
            float baseline = topY - CELL_PAD_Y - size;
            for (String line : wrapped.get(i)) {
                if (!line.isEmpty()) {
                    text(cs, font, size, color, x, baseline, line);
                }
                baseline -= lineHeight;
            }
        }
    }

    private static void text(PDPageContentStream cs, PDFont font, float size, Color color, float x, float y, String value)
            throws IOException {
        PdfText.draw(cs, font, size, color, x, y, value);
    }

    private static void rule(PDPageContentStream cs, float x1, float y1, float x2, float y2) throws IOException {
        cs.setStrokingColor(RULE);
        cs.setLineWidth(0.5f);
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
    }
}
