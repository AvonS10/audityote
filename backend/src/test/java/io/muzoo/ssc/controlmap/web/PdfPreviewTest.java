package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.report.RenderedReport;
import io.muzoo.ssc.controlmap.repository.UserRepository;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev-only visual check, NOT part of the suite (guarded by {@code -Dpdf.preview=true}): renders the
 * posture report from the current dev database and writes each PDF page as a PNG under
 * {@code target/posture-preview/} so the layout can be eyeballed — the same "screenshot the result"
 * discipline used for screens, applied to the PDF artifact. PDFBox's own renderer; no extra tooling.
 */
@SpringBootTest
@Transactional(readOnly = true)
@EnabledIfSystemProperty(named = "pdf.preview", matches = "true")
class PdfPreviewTest {

    @Autowired private PostureReportService postureReportService;
    @Autowired private UserRepository users;

    @Test
    void renderPostureReportPagesToPng() throws Exception {
        String actor = users.findAll().stream().findFirst().map(u -> u.getEmail()).orElse("preview@local");
        RenderedReport report = postureReportService.postureReport("pdf", actor);

        Path out = Path.of("target", "posture-preview");
        Files.createDirectories(out);
        try (PDDocument doc = Loader.loadPDF(report.body())) {
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                File png = out.resolve("page-" + (i + 1) + ".png").toFile();
                ImageIO.write(renderer.renderImageWithDPI(i, 110), "png", png);
            }
        }
        Files.write(out.resolve("posture-preview.pdf"), report.body());
    }
}
