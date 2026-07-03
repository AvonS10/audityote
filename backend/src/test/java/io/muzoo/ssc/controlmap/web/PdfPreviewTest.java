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
 * posture report and the tabular registers (findings, audit log) from the current dev database and
 * writes each PDF page as a PNG under {@code target/<name>-preview/} so the layout can be eyeballed —
 * the same "screenshot the result" discipline used for screens, applied to the PDF artifacts.
 * PDFBox's own renderer; no extra tooling.
 */
@SpringBootTest
@Transactional(readOnly = true)
@EnabledIfSystemProperty(named = "pdf.preview", matches = "true")
class PdfPreviewTest {

    @Autowired private PostureReportService postureReportService;
    @Autowired private ReportService reportService;
    @Autowired private UserRepository users;

    @Test
    void renderPostureReportPagesToPng() throws Exception {
        renderPages(postureReportService.postureReport("pdf", anyActor()), "posture");
    }

    @Test
    void renderTabularReportPagesToPng() throws Exception {
        String actor = anyActor();
        renderPages(reportService.findingsReport("pdf", actor), "findings");
        renderPages(reportService.auditReport("pdf", actor), "audit-log");
        renderPages(reportService.userAuditReport("pdf", actor), "user-audit-log");
    }

    private String anyActor() {
        return users.findAll().stream().findFirst().map(u -> u.getEmail()).orElse("preview@local");
    }

    private static void renderPages(RenderedReport report, String name) throws Exception {
        Path out = Path.of("target", name + "-preview");
        Files.createDirectories(out);
        try (PDDocument doc = Loader.loadPDF(report.body())) {
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                File png = out.resolve("page-" + (i + 1) + ".png").toFile();
                ImageIO.write(renderer.renderImageWithDPI(i, 110), "png", png);
            }
        }
        Files.write(out.resolve(name + "-preview.pdf"), report.body());
    }
}
