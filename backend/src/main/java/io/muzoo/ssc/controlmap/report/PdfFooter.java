package io.muzoo.ssc.controlmap.report;

import java.awt.Color;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Second-pass page footers shared by every PDF report writer: a hairline, "AuditYote - &lt;title&gt;"
 * on the left and "Page N of M" on the right of every page — appended once the total page count is
 * known. Extracted from the posture document writer so the tabular reports (findings / coverage /
 * audit log) carry the same audit-evidence footer (DRY); colors are the Sovereign stone tokens.
 */
public final class PdfFooter {

    private static final PDFont FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final Color RULE = new Color(0xD9, 0xD4, 0xC8);   // stone-250
    private static final Color MUTED = new Color(0x6B, 0x72, 0x68);  // stone-500

    private PdfFooter() {
    }

    /** Stamps every page of {@code doc}; the footer sits in the bottom margin strip below {@code margin}. */
    public static void apply(PDDocument doc, String title, float margin, float usable) throws IOException {
        int total = doc.getNumberOfPages();
        for (int i = 0; i < total; i++) {
            PDPage page = doc.getPage(i);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page, AppendMode.APPEND, true, true)) {
                cs.setStrokingColor(RULE);
                cs.setLineWidth(0.5f);
                cs.moveTo(margin, margin - 10);
                cs.lineTo(margin + usable, margin - 10);
                cs.stroke();
                PdfText.draw(cs, FONT, 7, MUTED, margin, margin - 22, PdfText.sanitize("AuditYote - " + title));
                String pageNo = "Page " + (i + 1) + " of " + total;
                PdfText.draw(cs, FONT, 7, MUTED,
                        margin + usable - PdfText.width(FONT, 7, pageNo), margin - 22, pageNo);
            }
        }
    }
}
