package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.domain.AuditLog;
import io.muzoo.ssc.controlmap.domain.UserAuditLog;
import io.muzoo.ssc.controlmap.report.ReportData;
import io.muzoo.ssc.controlmap.report.ReportData.Column;
import io.muzoo.ssc.controlmap.report.ReportFactory;
import io.muzoo.ssc.controlmap.report.ReportFormat;
import io.muzoo.ssc.controlmap.report.RenderedReport;
import io.muzoo.ssc.controlmap.repository.AuditLogRepository;
import io.muzoo.ssc.controlmap.repository.FrameworkRepository;
import io.muzoo.ssc.controlmap.repository.UserAuditLogRepository;
import io.muzoo.ssc.controlmap.web.dto.ControlRef;
import io.muzoo.ssc.controlmap.web.dto.CoverageRow;
import io.muzoo.ssc.controlmap.web.dto.FindingSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles report content from the existing read services and hands it to the {@link ReportFactory}
 * for serialization (PLAN §10/§14). Gathering data and formatting it are kept separate: this service
 * builds a format-agnostic {@link ReportData} (columns + weights + a summary line + provenance meta);
 * the factory's writer turns it into bytes (a clean CSV table, or a paginated PDF that renders the
 * provenance block and the shared brand/page-number footer).
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    // Findings columns with relative widths — title/asset/controls get the room, codes/numbers stay tight.
    // Risk = the effective risk score (CVSS when recorded, else severity-derived), same as the SPA table.
    private static final List<Column> FINDING_COLUMNS = List.of(
            new Column("Reference", 1.1f),
            new Column("Title", 2.6f),
            new Column("Severity", 0.9f),
            new Column("CVSS", 0.6f),
            new Column("Risk", 0.8f),
            new Column("Status", 1.0f),
            new Column("Owner", 1.2f),
            new Column("Asset", 1.3f),
            new Column("Mapped controls", 1.8f),
            new Column("Updated", 0.9f));
    private static final List<Column> COVERAGE_COLUMNS = List.of(
            new Column("Code", 1.0f),
            new Column("Control", 3.0f),
            new Column("Findings", 0.8f),
            new Column("Highest severity", 1.2f),
            new Column("At risk", 0.8f));
    // Actor email pins identity: display names are editable, so a name alone can drift after the fact.
    private static final List<Column> AUDIT_COLUMNS = List.of(
            new Column("Timestamp", 1.6f),
            new Column("Finding", 1.0f),
            new Column("Title", 2.0f),
            new Column("Actor", 1.0f),
            new Column("Actor email", 1.8f),
            new Column("Action", 0.8f),
            new Column("From", 0.8f),
            new Column("To", 0.8f),
            new Column("Detail", 2.0f));

    // Both parties of an admin action are identified by name AND immutable email (same policy as Actor).
    private static final List<Column> USER_AUDIT_COLUMNS = List.of(
            new Column("Timestamp", 1.5f),
            new Column("Actor", 1.1f),
            new Column("Actor email", 1.7f),
            new Column("Action", 1.0f),
            new Column("Target user", 1.1f),
            new Column("Target email", 1.7f),
            new Column("Detail", 1.6f));

    /** Fixed-precision UTC timestamps for audit rows — sortable in CSV, clean in the PDF, seconds kept. */
    private static final DateTimeFormatter AUDIT_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
            .withZone(ZoneOffset.UTC);

    /** Classification stamped on every register — the exports carry the same sensitive content as the app. */
    private static final String CLASSIFICATION = "Internal - contains security-sensitive findings.";

    private final FindingService findingService;
    private final CoverageService coverageService;
    private final FrameworkRepository frameworks;
    private final AuditLogRepository auditLog;
    private final UserAuditLogRepository userAudit;
    private final ReportFactory reportFactory;
    private final ReportProvenance provenance;

    public ReportService(FindingService findingService, CoverageService coverageService,
                         FrameworkRepository frameworks, AuditLogRepository auditLog,
                         UserAuditLogRepository userAudit, ReportFactory reportFactory,
                         ReportProvenance provenance) {
        this.findingService = findingService;
        this.coverageService = coverageService;
        this.frameworks = frameworks;
        this.auditLog = auditLog;
        this.userAudit = userAudit;
        this.reportFactory = reportFactory;
        this.provenance = provenance;
    }

    public RenderedReport findingsReport(String format, String actorEmail) {
        ReportFormat fmt = parseFormat(format);
        List<FindingSummary> findings = findingService.listAllForExport();
        List<List<String>> rows = findings.stream().map(ReportService::findingRow).toList();
        ReportData data = new ReportData("Findings register", findingsSummary(findings),
                meta(actorEmail), FINDING_COLUMNS, rows);
        return render("findings", fmt, data);
    }

    public RenderedReport auditReport(String format, String actorEmail) {
        ReportFormat fmt = parseFormat(format);
        List<AuditLog> entries = auditLog.findAllForExport();
        List<List<String>> rows = entries.stream().map(ReportService::auditRow).toList();
        long findings = entries.stream().map(a -> a.getFinding().getId()).distinct().count();
        String subtitle = entries.size() + " events across " + findings + " findings (including deleted)";
        return render("audit-log", fmt, new ReportData("Audit log", subtitle, meta(actorEmail), AUDIT_COLUMNS, rows));
    }

    public RenderedReport coverageReport(String framework, String format, String actorEmail) {
        ReportFormat fmt = parseFormat(format);
        // Validates the framework (unknown → 404) and returns rows in catalog order.
        List<CoverageRow> coverage = coverageService.coverage(framework);
        List<List<String>> rows = coverage.stream().map(ReportService::coverageRow).toList();
        ReportData data = new ReportData(coverageTitle(framework), coverageSummary(coverage),
                meta(actorEmail), COVERAGE_COLUMNS, rows);
        return render("coverage-" + framework, fmt, data);
    }

    /** The admin user-management trail — access is ADMIN-gated at the controller. */
    public RenderedReport userAuditReport(String format, String actorEmail) {
        ReportFormat fmt = parseFormat(format);
        List<UserAuditLog> entries = userAudit.findAllForExport();
        List<List<String>> rows = entries.stream().map(ReportService::userAuditRow).toList();
        long targets = entries.stream().map(a -> a.getTargetUser().getId()).distinct().count();
        // Small counts are the norm here, so pluralize properly ("1 admin action across 1 user account").
        String subtitle = entries.size() + (entries.size() == 1 ? " admin action" : " admin actions")
                + " across " + targets + (targets == 1 ? " user account" : " user accounts");
        return render("user-audit-log", fmt,
                new ReportData("User management audit log", subtitle, meta(actorEmail), USER_AUDIT_COLUMNS, rows));
    }

    /** Provenance block for every export — the PDF renders it; the CSV stays a clean table without it. */
    private List<String> meta(String actorEmail) {
        return List.of(
                provenance.generatedLine(Instant.now()),
                provenance.generatedByLine(actorEmail),
                CLASSIFICATION);
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
                FindingMapper.riskToReportCell(f.riskScore(), f.riskSource()),
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

    private static List<String> auditRow(AuditLog a) {
        return List.of(
                AUDIT_TS.format(a.getTimestamp()),
                a.getFinding().getReference(),
                a.getFinding().getTitle(),
                a.getActor().getName(),
                a.getActor().getEmail(),
                a.getAction(),
                a.getFromStatus() == null ? "" : FindingMapper.statusToWire(a.getFromStatus()),
                a.getToStatus() == null ? "" : FindingMapper.statusToWire(a.getToStatus()),
                a.getComment() == null ? "" : a.getComment());
    }

    private static List<String> userAuditRow(UserAuditLog a) {
        return List.of(
                AUDIT_TS.format(a.getTimestamp()),
                a.getActor().getName(),
                a.getActor().getEmail(),
                a.getAction(),
                a.getTargetUser().getName(),
                a.getTargetUser().getEmail(),
                a.getDetail() == null ? "" : a.getDetail());
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
