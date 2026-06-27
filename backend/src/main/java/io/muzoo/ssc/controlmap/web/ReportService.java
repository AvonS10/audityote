package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.report.ReportData;
import io.muzoo.ssc.controlmap.report.ReportData.Column;
import io.muzoo.ssc.controlmap.report.ReportFactory;
import io.muzoo.ssc.controlmap.report.ReportFormat;
import io.muzoo.ssc.controlmap.report.RenderedReport;
import io.muzoo.ssc.controlmap.repository.FrameworkRepository;
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
 * builds a format-agnostic {@link ReportData} (columns + weights + a summary line); the factory's
 * writer turns it into bytes (CSV dump or a paginated PDF table).
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    // Findings columns with relative widths — title/asset/controls get the room, codes/numbers stay tight.
    private static final List<Column> FINDING_COLUMNS = List.of(
            new Column("Reference", 1.1f),
            new Column("Title", 2.7f),
            new Column("Severity", 0.9f),
            new Column("CVSS", 0.6f),
            new Column("Status", 1.0f),
            new Column("Owner", 1.2f),
            new Column("Asset", 1.4f),
            new Column("Mapped controls", 1.8f),
            new Column("Updated", 0.9f));
    private static final List<Column> COVERAGE_COLUMNS = List.of(
            new Column("Code", 1.0f),
            new Column("Control", 3.0f),
            new Column("Findings", 0.8f),
            new Column("Highest severity", 1.2f),
            new Column("At risk", 0.8f));

    private final FindingService findingService;
    private final CoverageService coverageService;
    private final FrameworkRepository frameworks;
    private final ReportFactory reportFactory;

    public ReportService(FindingService findingService, CoverageService coverageService,
                         FrameworkRepository frameworks, ReportFactory reportFactory) {
        this.findingService = findingService;
        this.coverageService = coverageService;
        this.frameworks = frameworks;
        this.reportFactory = reportFactory;
    }

    public RenderedReport findingsReport(String format) {
        ReportFormat fmt = parseFormat(format);
        List<FindingSummary> findings = findingService.listAllForExport();
        List<List<String>> rows = findings.stream().map(ReportService::findingRow).toList();
        ReportData data = new ReportData("Findings register", findingsSummary(findings), FINDING_COLUMNS, rows);
        return render("findings", fmt, data);
    }

    public RenderedReport coverageReport(String framework, String format) {
        ReportFormat fmt = parseFormat(format);
        // Validates the framework (unknown → 404) and returns rows in catalog order.
        List<CoverageRow> coverage = coverageService.coverage(framework);
        List<List<String>> rows = coverage.stream().map(ReportService::coverageRow).toList();
        ReportData data = new ReportData(coverageTitle(framework), coverageSummary(coverage), COVERAGE_COLUMNS, rows);
        return render("coverage-" + framework, fmt, data);
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

    // ---- rows ----

    private static List<String> findingRow(FindingSummary f) {
        return List.of(
                f.reference(),
                f.title(),
                f.severity(),
                f.cvss() == null ? "" : f.cvss().toPlainString(),
                f.status(),
                f.owner(),
                f.asset() == null ? "" : f.asset(),
                f.controls().stream().map(ReportService::controlRef).collect(Collectors.joining(", ")),
                // Date only — the wall-clock time is noise in a printed register.
                f.updatedAt().toString().substring(0, 10));
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

    // ---- summary lines (the headline numbers a printed report leads with) ----

    private static String findingsSummary(List<FindingSummary> findings) {
        long critical = countSeverity(findings, "critical");
        long high = countSeverity(findings, "high");
        long medium = countSeverity(findings, "medium");
        long low = countSeverity(findings, "low");
        return findings.size() + " findings  ·  " + critical + " critical  ·  " + high + " high  ·  "
                + medium + " medium  ·  " + low + " low";
    }

    private static long countSeverity(List<FindingSummary> findings, String severity) {
        return findings.stream().filter(f -> severity.equals(f.severity())).count();
    }

    private String coverageTitle(String slug) {
        return frameworks.findBySlug(slug)
                .map(fw -> "Control coverage - " + fw.getName() + " " + fw.getVersion())
                .orElse("Control coverage");
    }

    private static String coverageSummary(List<CoverageRow> rows) {
        int total = rows.size();
        long covered = rows.stream().filter(r -> r.findingCount() > 0).count();
        long atRisk = rows.stream().filter(CoverageRow::atRisk).count();
        long gaps = total - covered;
        int pct = total == 0 ? 0 : Math.round(covered * 100f / total);
        return pct + "% covered  ·  " + covered + " covered  ·  " + atRisk + " at-risk  ·  "
                + gaps + " gaps  ·  " + total + " controls";
    }
}
