package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.report.ReportData;
import io.muzoo.ssc.controlmap.report.ReportFactory;
import io.muzoo.ssc.controlmap.report.ReportFormat;
import io.muzoo.ssc.controlmap.report.RenderedReport;
import io.muzoo.ssc.controlmap.web.dto.ControlRef;
import io.muzoo.ssc.controlmap.web.dto.CoverageRow;
import io.muzoo.ssc.controlmap.web.dto.FindingSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles report content from the existing read services and hands it to the {@link ReportFactory}
 * for serialization (PLAN §10/§14). Gathering data and formatting it are kept separate: this service
 * builds format-agnostic {@link ReportData}; the factory's writer turns it into bytes.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final List<String> FINDING_HEADERS =
            List.of("Reference", "Title", "Severity", "CVSS", "Status", "Owner", "Asset", "Mapped controls", "Updated");
    private static final List<String> COVERAGE_HEADERS =
            List.of("Code", "Control", "Findings", "Highest severity", "At risk");

    private final FindingService findingService;
    private final CoverageService coverageService;
    private final ReportFactory reportFactory;

    public ReportService(FindingService findingService, CoverageService coverageService, ReportFactory reportFactory) {
        this.findingService = findingService;
        this.coverageService = coverageService;
        this.reportFactory = reportFactory;
    }

    public RenderedReport findingsReport(String format) {
        ReportFormat fmt = parseFormat(format);
        List<List<String>> rows = findingService.listAllForExport().stream()
                .map(ReportService::findingRow)
                .toList();
        return render("findings", fmt, new ReportData("Findings", FINDING_HEADERS, rows));
    }

    public RenderedReport coverageReport(String framework, String format) {
        ReportFormat fmt = parseFormat(format);
        // Validates the framework (unknown → 404) and returns rows in catalog order.
        List<List<String>> rows = coverageService.coverage(framework).stream()
                .map(ReportService::coverageRow)
                .toList();
        return render("coverage-" + framework, fmt, new ReportData("Coverage", COVERAGE_HEADERS, rows));
    }

    private RenderedReport render(String baseName, ReportFormat fmt, ReportData data) {
        byte[] body = reportFactory.writerFor(fmt).write(data);
        String filename = baseName + "-" + LocalDate.now() + "." + fmt.extension();
        return new RenderedReport(filename, fmt.mediaType(), body);
    }

    private ReportFormat parseFormat(String format) {
        try {
            return ReportFormat.fromWire(format);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private static List<String> findingRow(FindingSummary f) {
        return List.of(
                f.reference(),
                f.title(),
                f.severity(),
                f.cvss() == null ? "" : f.cvss().toPlainString(),
                f.status(),
                f.owner(),
                f.asset() == null ? "" : f.asset(),
                f.controls().stream().map(ReportService::controlRef).collect(Collectors.joining("; ")),
                f.updatedAt().toString());
    }

    private static String controlRef(ControlRef c) {
        return c.framework() + ":" + c.code();
    }

    private static List<String> coverageRow(CoverageRow r) {
        return List.of(
                r.control().code(),
                r.control().title(),
                Long.toString(r.findingCount()),
                r.highestSeverity() == null ? "" : r.highestSeverity(),
                r.atRisk() ? "yes" : "no");
    }
}
