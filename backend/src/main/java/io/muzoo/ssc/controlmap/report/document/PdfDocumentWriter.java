package io.muzoo.ssc.controlmap.report.document;

import io.muzoo.ssc.controlmap.report.PdfFooter;
import io.muzoo.ssc.controlmap.report.PdfText;
import io.muzoo.ssc.controlmap.report.ReportData.Column;
import io.muzoo.ssc.controlmap.report.ReportFormat;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

/**
 * Renders a {@link ReportDocument} as an A4 <em>portrait</em> management/auditor PDF: a cover page
 * (title, provenance, headline score chip), then the sections in order — KPI tiles, the posture gauge
 * with its band scale, direct-labelled bar charts, the severity×status heatmap, tables that repeat
 * their header across page breaks, rule-derived insights, and prose. Every page gets a footer with
 * page numbers (second pass, once the total is known). All charts are drawn with PDFBox primitives
 * (rects, bezier arcs, text) — no chart library.
 *
 * <p><strong>Color & accessibility.</strong> Colors mirror the Sovereign design tokens
 * ({@code design-system/tokens/colors.css}) — severity/status colors are semantic and fixed (CLAUDE.md).
 * A CVD check of the severity set shows medium↔high are not separable by hue for deuteranopes, so
 * identity NEVER rides on color alone here: every bar/row/cell is direct-labelled with its name and
 * count (the dataviz "secondary encoding" rule), and the heatmap tint is a single-hue sequential ramp,
 * not a categorical recoloring. Numbers, references and scores render in the mono font (design rule).
 */
@Component
public class PdfDocumentWriter implements DocumentWriter {

    // ---- fonts: Times = serif display (Spectral stand-in), Helvetica = body, Courier = mono data ----
    private static final PDFont DISPLAY = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
    private static final PDFont BODY = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BODY_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont BODY_ITALIC = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
    private static final PDFont MONO = new PDType1Font(Standard14Fonts.FontName.COURIER);
    private static final PDFont MONO_BOLD = new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);

    // ---- Sovereign token colors (design-system/tokens/colors.css — keep in sync) ----
    private static final Color TEXT = new Color(0x1C, 0x21, 0x1D);        // stone-900
    private static final Color MUTED = new Color(0x6B, 0x72, 0x68);       // stone-500
    private static final Color FAINT = new Color(0x9A, 0xA0, 0x95);       // stone-400
    private static final Color INSET = new Color(0xED, 0xEA, 0xE2);       // stone-150 (bar tracks, empty cells)
    private static final Color BORDER = new Color(0xE7, 0xE3, 0xDA);      // stone-200
    private static final Color RULE = new Color(0xD9, 0xD4, 0xC8);        // stone-250
    private static final Color FOREST = new Color(0x1F, 0x4A, 0x3D);      // forest-700 (brand)
    private static final Color HEAT_BASE = new Color(0xB2, 0x3A, 0x2E);   // critical-500 (heatmap ramp)
    private static final Color WHITE = Color.WHITE;

    /** Semantic color keys (severity bands, workflow statuses, posture bands) → token colors. */
    private static final Map<String, Color> KEY_COLORS = Map.ofEntries(
            Map.entry("critical", new Color(0x8F, 0x1C, 0x16)),           // critical-600
            Map.entry("high", new Color(0xA8, 0x47, 0x0F)),               // high-600
            Map.entry("medium", new Color(0x97, 0x70, 0x0E)),             // medium-600
            Map.entry("low", new Color(0x2A, 0x5E, 0x4D)),                // low-600
            Map.entry("positive", new Color(0x2F, 0x7D, 0x3E)),           // positive-600 (band "Low")
            Map.entry("open", new Color(0x6B, 0x72, 0x68)),               // --status-open
            Map.entry("in-progress", new Color(0x2A, 0x5E, 0x4D)),        // --status-progress
            Map.entry("submitted", new Color(0x0E, 0x6E, 0x72)),          // --status-submitted
            Map.entry("returned", new Color(0xA8, 0x47, 0x0F)),           // --status-returned
            Map.entry("approved", new Color(0x2F, 0x7D, 0x3E)),           // --status-approved
            Map.entry("remediated", new Color(0x1F, 0x6B, 0x4C)),         // --status-remediated
            Map.entry("accepted", new Color(0x6B, 0x5E, 0x45)));          // --status-accepted

    private static final float PAGE_W = PDRectangle.A4.getWidth();
    private static final float PAGE_H = PDRectangle.A4.getHeight();
    private static final float MARGIN = 46f;
    private static final float USABLE = PAGE_W - 2 * MARGIN;
    private static final float FOOTER_CLEAR = 34f;                        // content never enters this strip

    @Override
    public ReportFormat format() {
        return ReportFormat.PDF;
    }

    @Override
    public byte[] write(ReportDocument document) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            new Renderer(doc).render(document);
            PdfFooter.apply(doc, document.title(), MARGIN, USABLE);
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render PDF posture report", e);
        }
    }

    private static Color keyColor(String key) {
        return KEY_COLORS.getOrDefault(key, MUTED);
    }

    /** Blends {@code color} into white at {@code pct}% — the PDF analogue of the SPA's color-mix(). */
    private static Color tint(Color color, int pct) {
        float p = Math.max(0, Math.min(100, pct)) / 100f;
        return new Color(
                Math.round(color.getRed() * p + 255 * (1 - p)),
                Math.round(color.getGreen() * p + 255 * (1 - p)),
                Math.round(color.getBlue() * p + 255 * (1 - p)));
    }

    /** Per-call rendering state (the writer bean itself stays stateless/thread-safe). */
    private static final class Renderer {

        private final PDDocument doc;
        private PDPageContentStream cs;
        private float y;

        Renderer(PDDocument doc) {
            this.doc = doc;
        }

        void render(ReportDocument document) throws IOException {
            cover(document);
            newPage();
            for (ReportSection section : document.sections()) {
                switch (section) {
                    case ReportSection.Kpis s -> kpis(s);
                    case ReportSection.Gauge s -> gauge(s);
                    case ReportSection.BarChart s -> barChart(s);
                    case ReportSection.Heatmap s -> heatmap(s);
                    case ReportSection.TableSection s -> table(s);
                    case ReportSection.Insights s -> insights(s);
                    case ReportSection.Paragraphs s -> paragraphs(s);
                }
                y -= 20;                                               // inter-section gap
            }
            cs.close();
        }

        // ---- page management ----

        private void newPage() throws IOException {
            if (cs != null) {
                cs.close();
            }
            PDPage page = new PDPage(new PDRectangle(PAGE_W, PAGE_H));
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            y = PAGE_H - MARGIN;
        }

        /** Starts a new page unless {@code needed} points of content still fit above the footer strip. */
        private void ensure(float needed) throws IOException {
            if (y - needed < MARGIN + FOOTER_CLEAR) {
                newPage();
            }
        }

        // ---- shared bits ----

        private void text(PDFont font, float size, Color color, float x, float baselineY, String value) throws IOException {
            PdfText.draw(cs, font, size, color, x, baselineY, PdfText.sanitize(value));
        }

        private void textRight(PDFont font, float size, Color color, float rightX, float baselineY, String value)
                throws IOException {
            String v = PdfText.sanitize(value);
            PdfText.draw(cs, font, size, color, rightX - PdfText.width(font, size, v), baselineY, v);
        }

        private void rect(Color fill, float x, float yTop, float w, float h) throws IOException {
            cs.setNonStrokingColor(fill);
            cs.addRect(x, yTop - h, w, h);
            cs.fill();
        }

        private void outline(Color stroke, float x, float yTop, float w, float h) throws IOException {
            cs.setStrokingColor(stroke);
            cs.setLineWidth(0.75f);
            cs.addRect(x, yTop - h, w, h);
            cs.stroke();
        }

        private void hairline(float x1, float yLine, float x2) throws IOException {
            cs.setStrokingColor(RULE);
            cs.setLineWidth(0.5f);
            cs.moveTo(x1, yLine);
            cs.lineTo(x2, yLine);
            cs.stroke();
        }

        /** Section heading: serif title + hairline, with keep-together space for what follows. */
        private void heading(String title, float keepWith) throws IOException {
            ensure(34 + keepWith);
            text(DISPLAY, 13.5f, TEXT, MARGIN, y - 13.5f, title);
            y -= 20;
            hairline(MARGIN, y, MARGIN + USABLE);
            y -= 14;
        }

        // ---- cover ----

        private void cover(ReportDocument d) throws IOException {
            newPage();
            // Brand wordmark: "Audit" in forest + "Yote" dimmed (the AppShell treatment).
            float wy = PAGE_H - 74;
            text(DISPLAY, 21, FOREST, MARGIN, wy, "Audit");
            PdfText.draw(cs, DISPLAY, 21, FAINT, MARGIN + PdfText.width(DISPLAY, 21, "Audit"), wy, "Yote");
            text(BODY_BOLD, 7.5f, MUTED, MARGIN, wy - 14, "GOVERNANCE, RISK & COMPLIANCE");
            hairline(MARGIN, wy - 26, MARGIN + USABLE);

            // Title block.
            float ty = PAGE_H - 240;
            text(DISPLAY, 29, TEXT, MARGIN, ty, d.title());
            text(BODY, 11.5f, MUTED, MARGIN, ty - 22, d.subtitle());

            // Provenance meta lines (audit evidence: who generated it, when, over what).
            float my = ty - 64;
            for (String line : d.metaLines()) {
                text(BODY, 9.5f, MUTED, MARGIN, my, line);
                my -= 16;
            }

            // Headline chip: score + band in a tinted, band-colored box.
            Color band = keyColor(d.headline().colorKey());
            float boxTop = my - 26;
            float boxH = 92;
            rect(tint(band, 7), MARGIN, boxTop, USABLE, boxH);
            outline(tint(band, 40), MARGIN, boxTop, USABLE, boxH);
            String score = String.valueOf(d.headline().score());
            text(MONO_BOLD, 44, band, MARGIN + 26, boxTop - 62, score);
            float lx = MARGIN + 26 + PdfText.width(MONO_BOLD, 44, score) + 8;
            text(MONO, 13, MUTED, lx, boxTop - 62, "/ 100");
            float rx = MARGIN + 200;
            text(BODY_BOLD, 15, band, rx, boxTop - 38, d.headline().bandLabel().toUpperCase(java.util.Locale.ROOT));
            text(BODY, 8.5f, MUTED, rx, boxTop - 56, "Overall risk posture (0-100, higher = worse).");
            text(BODY, 8.5f, MUTED, rx, boxTop - 69, "Derived from all active findings - see Methodology.");

            // Classification, pinned to the bottom.
            hairline(MARGIN, MARGIN + 26, MARGIN + USABLE);
            text(BODY_BOLD, 8, MUTED, MARGIN, MARGIN + 12, d.classification());
        }

        // ---- sections ----

        private void kpis(ReportSection.Kpis s) throws IOException {
            float gap = 10;
            float w = (USABLE - gap * (s.kpis().size() - 1)) / s.kpis().size();
            float h = 52;
            ensure(h + 4);
            float x = MARGIN;
            for (ReportSection.Kpis.Kpi kpi : s.kpis()) {
                outline(BORDER, x, y, w, h);
                float inner = w - 18;                                    // clip to the tile, never overflow it
                text(BODY_BOLD, 6.8f, MUTED, x + 9, y - 14,
                        firstLine(kpi.label().toUpperCase(java.util.Locale.ROOT), BODY_BOLD, 6.8f, inner));
                text(MONO_BOLD, 17, TEXT, x + 9, y - 33, kpi.value());
                text(BODY, 7, FAINT, x + 9, y - 45, firstLine(kpi.caption(), BODY, 7, inner));
                x += w + gap;
            }
            y -= h;
        }

        private void gauge(ReportSection.Gauge s) throws IOException {
            float r = 74;
            float stroke = 11;
            float scaleBlock = 44;
            float blockH = r + stroke + 30 + scaleBlock;
            heading(s.title(), blockH);
            ensure(blockH);

            float cx = MARGIN + USABLE / 2;
            float cy = y - (r + stroke);                                // arc baseline (gauge sits on it)
            cs.setLineCapStyle(1);                                      // round caps, like the SPA gauge
            arc(cx, cy, r, 180, 0, stroke, INSET);                      // track
            float sweep = Math.max(2, s.score() / 100f * 180f);         // ≥2° so a tiny score stays visible
            Color band = keyColor(currentBand(s).colorKey());
            if (s.score() > 0) {
                arc(cx, cy, r, 180, 180 - sweep, stroke, band);         // value, left → right
            }
            String score = String.valueOf(s.score());
            PdfText.draw(cs, MONO_BOLD, 30, TEXT,
                    cx - PdfText.width(MONO_BOLD, 30, score) / 2, cy + 16, score);
            String bandLabel = s.bandLabel().toUpperCase(java.util.Locale.ROOT);
            PdfText.draw(cs, BODY_BOLD, 9, band,
                    cx - PdfText.width(BODY_BOLD, 9, bandLabel) / 2, cy + 3, bandLabel);
            y = cy - 18;

            // Band scale: segment widths proportional to each band's score range; current = full color.
            float sy = y;
            float prev = 0;
            float x = MARGIN;
            String current = currentBand(s).label();
            for (ReportSection.Gauge.Band b : s.bands()) {
                float w = (Math.min(b.max(), 100) - prev) / 100f * USABLE;
                Color c = keyColor(b.colorKey());
                boolean cur = b.label().equals(current);
                rect(cur ? c : tint(c, 22), x, sy, w - 3, 5);
                PDFont f = cur ? BODY_BOLD : BODY;
                PdfText.draw(cs, f, 7.5f, cur ? c : FAINT,
                        x + (w - 3) / 2 - PdfText.width(f, 7.5f, b.label()) / 2, sy - 16, b.label());
                prev = Math.min(b.max(), 100);
                x += w;
            }
            // Score marker: a small tick above the scale at the score position.
            float mx = MARGIN + s.score() / 100f * USABLE;
            cs.setStrokingColor(TEXT);
            cs.setLineWidth(1.2f);
            cs.moveTo(mx, sy + 3);
            cs.lineTo(mx, sy - 6);
            cs.stroke();
            y = sy - 30;
        }

        /** Circular arc stroked via ≤45° cubic-bezier segments (PDF has no arc primitive). */
        private void arc(float cx, float cy, float r, float fromDeg, float toDeg, float stroke, Color color)
                throws IOException {
            cs.setStrokingColor(color);
            cs.setLineWidth(stroke);
            int steps = Math.max(1, (int) Math.ceil(Math.abs(fromDeg - toDeg) / 45f));
            double a0 = Math.toRadians(fromDeg);
            double step = Math.toRadians(toDeg - fromDeg) / steps;
            cs.moveTo(cx + r * (float) Math.cos(a0), cy + r * (float) Math.sin(a0));
            for (int i = 0; i < steps; i++) {
                double s0 = a0 + i * step;
                double s1 = s0 + step;
                double k = 4f / 3f * Math.tan((s1 - s0) / 4);
                float x0 = cx + r * (float) Math.cos(s0);
                float y0 = cy + r * (float) Math.sin(s0);
                float x3 = cx + r * (float) Math.cos(s1);
                float y3 = cy + r * (float) Math.sin(s1);
                float x1 = x0 - (float) (k * r * Math.sin(s0));
                float y1 = y0 + (float) (k * r * Math.cos(s0));
                float x2 = x3 + (float) (k * r * Math.sin(s1));
                float y2 = y3 - (float) (k * r * Math.cos(s1));
                cs.curveTo(x1, y1, x2, y2, x3, y3);
            }
            cs.stroke();
        }

        private static ReportSection.Gauge.Band currentBand(ReportSection.Gauge s) {
            return s.bands().stream()
                    .filter(b -> s.score() < b.max())
                    .findFirst()
                    .orElse(s.bands().get(s.bands().size() - 1));
        }

        private void barChart(ReportSection.BarChart s) throws IOException {
            float rowH = 24;
            heading(s.title(), Math.min(s.bars().size(), 3) * rowH);
            long max = Math.max(1, s.bars().stream().mapToLong(ReportSection.BarChart.Bar::count).max().orElse(1));
            long total = Math.max(1, s.bars().stream().mapToLong(ReportSection.BarChart.Bar::count).sum());
            float labelW = 108;
            float countW = 76;
            float trackW = USABLE - labelW - countW;
            for (ReportSection.BarChart.Bar bar : s.bars()) {
                ensure(rowH);
                Color c = keyColor(bar.colorKey());
                rect(c, MARGIN, y - 3, 7, 7);                            // identity chip
                text(BODY_BOLD, 8.5f, TEXT, MARGIN + 12, y - 10, bar.label());
                rect(INSET, MARGIN + labelW, y - 2, trackW, 9);          // track
                float w = bar.count() / (float) max * trackW;
                if (bar.count() > 0) {
                    rect(c, MARGIN + labelW, y - 2, w, 9);               // fill
                }
                String count = String.valueOf(bar.count());
                text(MONO_BOLD, 8.5f, TEXT, MARGIN + labelW + trackW + 10, y - 10, count);
                text(BODY, 7.5f, MUTED, MARGIN + labelW + trackW + 10 + PdfText.width(MONO_BOLD, 8.5f, count) + 5,
                        y - 10, Math.round(bar.count() * 100f / total) + "%");
                y -= rowH;
            }
            if (s.note() != null && !s.note().isBlank()) {
                ensure(12);
                text(BODY, 7.5f, FAINT, MARGIN, y - 8, s.note());
                y -= 14;
            }
        }

        private void heatmap(ReportSection.Heatmap s) throws IOException {
            float labelW = 86;
            float gap = 3;
            int cols = s.colLabels().size();
            float cellW = (USABLE - labelW - gap * (cols - 1)) / cols;
            float cellH = 24;
            float block = 14 + s.rows().size() * (cellH + gap) + 24;
            heading(s.title(), Math.min(block, 160));
            ensure(block);

            float x = MARGIN + labelW;
            for (String col : s.colLabels()) {                           // column headers
                String v = PdfText.sanitize(col);
                PdfText.draw(cs, BODY_BOLD, 7, MUTED, x + cellW / 2 - PdfText.width(BODY_BOLD, 7, v) / 2, y - 8, v);
                x += cellW + gap;
            }
            y -= 14;

            for (ReportSection.Heatmap.Row row : s.rows()) {
                rect(keyColor(row.colorKey()), MARGIN, y - (cellH / 2 - 3), 7, 7);
                text(BODY_BOLD, 8.5f, TEXT, MARGIN + 12, y - (cellH / 2 + 3), row.label());
                x = MARGIN + labelW;
                for (ReportSection.Heatmap.Cell cell : row.cells()) {
                    boolean empty = cell.count() == 0;
                    // Same policy as the SPA heatmap: tint = inherent risk, single-hue ramp of critical-500;
                    // empty cells recede to the inset neutral; hot cells (>50%) flip to white text.
                    if (empty) {
                        rect(INSET, x, y, cellW, cellH);
                    } else {
                        rect(tint(HEAT_BASE, cell.intensityPct()), x, y, cellW, cellH);
                    }
                    String v = empty ? "-" : String.valueOf(cell.count());
                    Color ink = empty ? FAINT : (cell.intensityPct() > 50 ? WHITE : TEXT);
                    PdfText.draw(cs, MONO_BOLD, 9, ink,
                            x + cellW / 2 - PdfText.width(MONO_BOLD, 9, v) / 2, y - cellH / 2 - 3, v);
                    x += cellW + gap;
                }
                y -= cellH + gap;
            }

            // Legend: caption + a Lower→Higher ramp (drawn as blended slivers).
            y -= 6;
            text(BODY, 7.5f, MUTED, MARGIN, y - 8, s.note());
            float rampW = 110;
            float rampX = MARGIN + USABLE - rampW;
            textRight(BODY, 7, FAINT, rampX - 6, y - 8, "Lower");
            for (int i = 0; i < 22; i++) {
                rect(tint(HEAT_BASE, 6 + (int) Math.round(i * 94.0 / 21)), rampX + i * (rampW / 22f), y - 2, rampW / 22f, 7);
            }
            text(BODY, 7, FAINT, rampX + rampW + 6, y - 8, "Higher");
            y -= 16;
        }

        private void table(ReportSection.TableSection s) throws IOException {
            heading(s.title(), 60);
            if (s.note() != null && !s.note().isBlank()) {
                ensure(14);
                text(BODY, 8, MUTED, MARGIN, y - 8, s.note());
                y -= 18;
            }
            float[] colX = new float[s.columns().size()];
            float[] colW = new float[s.columns().size()];
            layoutColumns(s.columns(), colX, colW);

            if (s.rows().isEmpty()) {
                ensure(14);
                text(BODY_ITALIC, 8.5f, FAINT, MARGIN, y - 9, "None.");
                y -= 16;
                return;
            }
            drawTableHeader(s, colX, colW);
            for (List<String> row : s.rows()) {
                List<List<String>> wrapped = wrapCells(row, colW, BODY, 8);
                float h = rowHeight(wrapped, 8);
                if (y - h < MARGIN + FOOTER_CLEAR) {
                    newPage();
                    drawTableHeader(s, colX, colW);                      // header repeats on every page
                }
                paintCells(wrapped, colX, BODY, 8, TEXT);
                y -= h;
                hairline(MARGIN, y, MARGIN + USABLE);
            }
        }

        private void drawTableHeader(ReportSection.TableSection s, float[] colX, float[] colW) throws IOException {
            List<String> headers = s.columns().stream().map(Column::header).toList();
            List<List<String>> wrapped = wrapCells(headers, colW, BODY_BOLD, 8);
            float h = rowHeight(wrapped, 8);
            rect(INSET, MARGIN, y, USABLE, h);
            paintCells(wrapped, colX, BODY_BOLD, 8, TEXT);
            y -= h;
            hairline(MARGIN, y, MARGIN + USABLE);
        }

        private static void layoutColumns(List<Column> columns, float[] colX, float[] colW) {
            float sum = 0f;
            for (Column c : columns) {
                sum += c.weight();
            }
            if (sum <= 0f) {
                sum = Math.max(1, columns.size());
            }
            float x = MARGIN;
            for (int i = 0; i < columns.size(); i++) {
                colW[i] = USABLE * columns.get(i).weight() / sum;
                colX[i] = x;
                x += colW[i];
            }
        }

        private List<List<String>> wrapCells(List<String> cells, float[] colW, PDFont font, float size) throws IOException {
            List<List<String>> wrapped = new java.util.ArrayList<>(cells.size());
            for (int i = 0; i < cells.size(); i++) {
                wrapped.add(PdfText.wrap(PdfText.sanitize(cells.get(i)), font, size, colW[i] - 8));
            }
            return wrapped;
        }

        private static float rowHeight(List<List<String>> wrapped, float size) {
            int maxLines = 1;
            for (List<String> lines : wrapped) {
                maxLines = Math.max(maxLines, lines.size());
            }
            return maxLines * (size + 2.6f) + 8;
        }

        private void paintCells(List<List<String>> wrapped, float[] colX, PDFont font, float size, Color color)
                throws IOException {
            for (int i = 0; i < wrapped.size(); i++) {
                float baseline = y - 4 - size;
                for (String line : wrapped.get(i)) {
                    if (!line.isEmpty()) {
                        PdfText.draw(cs, font, size, color, colX[i] + 4, baseline, line);
                    }
                    baseline -= size + 2.6f;
                }
            }
        }

        private void insights(ReportSection.Insights s) throws IOException {
            heading(s.title(), 40);
            bullets(s.bullets(), TEXT);
            if (!s.recommendations().isEmpty()) {
                ensure(30);
                y -= 4;
                text(BODY_BOLD, 9, TEXT, MARGIN, y - 9, "Recommended actions");
                y -= 18;
                int n = 1;
                for (String rec : s.recommendations()) {
                    List<String> lines = PdfText.wrap(PdfText.sanitize(rec), BODY, 9, USABLE - 18);
                    ensure(lines.size() * 13 + 3);
                    text(MONO_BOLD, 9, FOREST, MARGIN, y - 9, n + ".");
                    for (String line : lines) {
                        text(BODY, 9, TEXT, MARGIN + 18, y - 9, line);
                        y -= 13;
                    }
                    y -= 3;
                    n++;
                }
            }
            y -= 4;
            for (String line : PdfText.wrap(PdfText.sanitize(s.caveat()), BODY_ITALIC, 7.5f, USABLE)) {
                ensure(11);
                text(BODY_ITALIC, 7.5f, FAINT, MARGIN, y - 8, line);
                y -= 11;
            }
            y -= 3;
        }

        /** The first wrap line that fits {@code maxWidth}, with "..." when content had to be dropped. */
        private static String firstLine(String value, PDFont font, float size, float maxWidth) throws IOException {
            List<String> lines = PdfText.wrap(PdfText.sanitize(value), font, size, maxWidth);
            return lines.size() <= 1 ? lines.get(0) : lines.get(0) + "...";
        }

        private void bullets(List<String> items, Color ink) throws IOException {
            for (String item : items) {
                List<String> lines = PdfText.wrap(PdfText.sanitize(item), BODY, 9, USABLE - 14);
                ensure(lines.size() * 13 + 3);
                text(BODY, 9, ink, MARGIN + 2, y - 9, "-");
                for (String line : lines) {
                    text(BODY, 9, ink, MARGIN + 14, y - 9, line);
                    y -= 13;
                }
                y -= 3;
            }
        }

        private void paragraphs(ReportSection.Paragraphs s) throws IOException {
            heading(s.title(), 30);
            for (String para : s.lines()) {
                List<String> lines = PdfText.wrap(PdfText.sanitize(para), BODY, 8.5f, USABLE);
                ensure(lines.size() * 12.5f + 5);
                for (String line : lines) {
                    text(BODY, 8.5f, MUTED, MARGIN, y - 9, line);
                    y -= 12.5f;
                }
                y -= 5;
            }
        }
    }

}
