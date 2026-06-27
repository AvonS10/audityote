package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.report.RenderedReport;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Report export API (PLAN §10), authenticated (any signed-in user). Streams a findings or coverage
 * report as a file download; the {@code ?format=} selects the format (CSV now). Unknown format → 400,
 * unknown framework → 404 (consistent error model). Reports are built by {@link ReportService} via
 * the ReportFactory.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/findings")
    public ResponseEntity<byte[]> findings(@RequestParam(value = "format", defaultValue = "csv") String format) {
        return download(reportService.findingsReport(format));
    }

    @GetMapping("/coverage")
    public ResponseEntity<byte[]> coverage(
            @RequestParam("framework") String framework,
            @RequestParam(value = "format", defaultValue = "csv") String format) {
        return download(reportService.coverageReport(framework, format));
    }

    @GetMapping("/audit")
    public ResponseEntity<byte[]> audit(@RequestParam(value = "format", defaultValue = "csv") String format) {
        return download(reportService.auditReport(format));
    }

    private static ResponseEntity<byte[]> download(RenderedReport report) {
        ContentDisposition disposition = ContentDisposition.attachment().filename(report.filename()).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(report.contentType()))
                .body(report.body());
    }
}
