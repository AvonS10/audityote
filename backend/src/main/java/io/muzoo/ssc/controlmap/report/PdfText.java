package io.muzoo.ssc.controlmap.report;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;

/**
 * Shared PDFBox text primitives — measuring, greedy word-wrap, Standard-14 sanitizing, and a one-call
 * text draw. Extracted from {@code PdfReportWriter} so the document writer (posture report) renders
 * text identically to the tabular reports (one wrap algorithm, one encoding policy — DRY).
 */
public final class PdfText {

    private PdfText() {
    }

    public static void draw(PDPageContentStream cs, PDFont font, float size, Color color, float x, float y, String value)
            throws IOException {
        cs.beginText();
        cs.setNonStrokingColor(color);
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(value);
        cs.endText();
    }

    public static float width(PDFont font, float size, String value) throws IOException {
        return value.isEmpty() ? 0f : font.getStringWidth(value) / 1000f * size;
    }

    /** Greedy word-wrap to a width; a single over-long word is hard-broken across lines. */
    public static List<String> wrap(String value, PDFont font, float size, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        String current = "";
        for (String word : value.split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            while (width(font, size, word) > maxWidth && word.length() > 1) {
                if (!current.isEmpty()) {
                    lines.add(current);
                    current = "";
                }
                int cut = fitPrefix(word, font, size, maxWidth);
                lines.add(word.substring(0, cut));
                word = word.substring(cut);
            }
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (width(font, size, candidate) <= maxWidth) {
                current = candidate;
            } else {
                if (!current.isEmpty()) {
                    lines.add(current);
                }
                current = word;
            }
        }
        if (lines.isEmpty() || !current.isEmpty()) {
            lines.add(current);
        }
        return lines;
    }

    /** Largest prefix length of {@code word} that fits {@code maxWidth} (at least 1 char). */
    private static int fitPrefix(String word, PDFont font, float size, float maxWidth) throws IOException {
        int i = 1;
        while (i < word.length() && width(font, size, word.substring(0, i + 1)) <= maxWidth) {
            i++;
        }
        return i;
    }

    /**
     * Maps text to what the Standard-14 fonts can encode: printable ASCII + Latin-1 pass through;
     * common typographic punctuation (em/en dashes, the → arrow, smart quotes, ellipsis) is
     * transliterated to ASCII so it renders cleanly instead of as '?'; anything else becomes '?'.
     */
    public static String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == 0x2014 || c == 0x2013 || c == 0x2012 || c == 0x2212) {
                sb.append('-');               // em / en / figure dash, minus sign
            } else if (c == 0x2192) {
                sb.append("->");              // right arrow (edit summaries: "low→critical")
            } else if (c == 0x2018 || c == 0x2019 || c == 0x201A) {
                sb.append('\'');              // smart single quotes
            } else if (c == 0x201C || c == 0x201D || c == 0x201E) {
                sb.append('"');               // smart double quotes
            } else if (c == 0x2026) {
                sb.append("...");             // ellipsis
            } else if ((c >= 0x20 && c <= 0x7E) || (c >= 0xA0 && c <= 0xFF)) {
                sb.append(c);
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }
}
