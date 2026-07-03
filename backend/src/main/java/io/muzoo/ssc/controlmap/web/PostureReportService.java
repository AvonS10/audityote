package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.domain.AuditLog;
import io.muzoo.ssc.controlmap.domain.FindingStatus;
import io.muzoo.ssc.controlmap.domain.Framework;
import io.muzoo.ssc.controlmap.report.RenderedReport;
import io.muzoo.ssc.controlmap.report.ReportData.Column;
import io.muzoo.ssc.controlmap.report.ReportFactory;
import io.muzoo.ssc.controlmap.report.ReportFormat;
import io.muzoo.ssc.controlmap.report.document.ReportDocument;
import io.muzoo.ssc.controlmap.report.document.ReportSection;
import io.muzoo.ssc.controlmap.repository.AuditLogRepository;
import io.muzoo.ssc.controlmap.repository.FrameworkRepository;
import io.muzoo.ssc.controlmap.web.dto.CoverageRow;
import io.muzoo.ssc.controlmap.web.dto.FindingSummary;
import io.muzoo.ssc.controlmap.web.dto.PostureResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the comprehensive posture/auditor report (PLAN roadmap #3): gathers the live numbers from
 * the existing read services (PostureService rollup, findings export, coverage per framework, audit
 * trail for acceptance provenance), synthesizes the rule-based insights, and composes a format-agnostic
 * {@link ReportDocument} that the {@link ReportFactory}'s document writer renders (PDF or CSV). SRP:
 * this class only <em>assembles</em> — scoring stays in PostureService, rendering in the writers,
 * interpretation rules in {@link PostureInsights}.
 */
@Service
@Transactional(readOnly = true)
public class PostureReportService {

    /** Posture gauge bands (PLAN §9) — mirrors the SPA's POSTURE_BANDS (score &lt; max picks the band). */
    private static final List<ReportSection.Gauge.Band> BANDS = List.of(
            new ReportSection.Gauge.Band("Low", 25, "positive"),
            new ReportSection.Gauge.Band("Guarded", 50, "low"),
            new ReportSection.Gauge.Band("Elevated", 70, "medium"),
            new ReportSection.Gauge.Band("High", 85, "high"),
            new ReportSection.Gauge.Band("Severe", 101, "critical"));

    /** Heatmap tint policy — severity weight × workflow-state factor, mirroring the SPA heatmap. */
    private static final Map<String, Integer> SEV_WEIGHT = Map.of(
            "critical", 4, "high", 3, "medium", 2, "low", 1);
    private static final Map<String, Double> STATUS_FACTOR = Map.of(
            "open", 1.0, "in-progress", 0.92, "submitted", 0.85, "returned", 0.9,
            "approved", 0.45, "remediated", 0.12, "accepted", 0.28);

    private static final int TOP_RISKS = 10;
    private static final Duration ACTIVITY_WINDOW = Duration.ofDays(90);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    /** Active statuses in wire form (single source: {@link PostureService#ACTIVE}). */
    private static final Set<String> ACTIVE_WIRE = PostureService.ACTIVE.stream()
            .map(FindingMapper::statusToWire)
            .collect(Collectors.toUnmodifiableSet());

    private final PostureService postureService;
    private final FindingService findingService;
    private final CoverageService coverageService;
    private final FrameworkRepository frameworks;
    private final AuditLogRepository auditLog;
    private final ReportProvenance provenance;
    private final ReportFactory reportFactory;

    public PostureReportService(PostureService postureService, FindingService findingService,
                                CoverageService coverageService, FrameworkRepository frameworks,
                                AuditLogRepository auditLog, ReportProvenance provenance,
                                ReportFactory reportFactory) {
        this.postureService = postureService;
        this.findingService = findingService;
        this.coverageService = coverageService;
        this.frameworks = frameworks;
        this.auditLog = auditLog;
        this.provenance = provenance;
        this.reportFactory = reportFactory;
    }

    public RenderedReport postureReport(String format, String actorEmail) {
        ReportFormat fmt = parseFormat(format);
        Instant now = Instant.now();

        PostureResponse p = postureService.rollup();
        List<FindingSummary> all = findingService.listAllForExport();
        Map<Framework, List<CoverageRow>> coverage = new LinkedHashMap<>();
        for (Framework fw : frameworks.findAllByOrderByNameAsc()) {
            coverage.put(fw, coverageService.coverage(fw.getSlug()));
        }
        List<AuditLog> trail = auditLog.findAllForExport();

        ReportDocument document = compose(p, all, coverage, trail, actorEmail, now);
        byte[] body = reportFactory.documentWriterFor(fmt).write(document);
        String filename = "posture-" + LocalDate.now() + "." + fmt.extension();
        return new RenderedReport(filename, fmt.mediaType(), body);
    }

    // ---- document assembly ----

    private ReportDocument compose(PostureResponse p, List<FindingSummary> all,
                                   Map<Framework, List<CoverageRow>> coverage, List<AuditLog> trail,
                                   String actorEmail, Instant now) {
        String band = bandLabel(p.score());
        String bandKey = bandKey(p.score());
        long controlCount = coverage.values().stream().mapToLong(List::size).sum();

        List<ReportSection> sections = new ArrayList<>();
        sections.add(kpis(p));
        sections.add(new ReportSection.Gauge("Overall risk posture", p.score(), band, BANDS));
        sections.add(new ReportSection.BarChart("Findings by severity",
                "All recorded findings, excluding deleted.", severityBars(p)));
        sections.add(new ReportSection.BarChart("Findings by workflow status",
                "Workflow order; approved/remediated/accepted no longer add to the posture load.", statusBars(p)));
        sections.add(heatmap(p));
        sections.add(insights(p, all, coverage, now));
        sections.add(topRisksTable(all, now));
        sections.add(acceptancesTable(p, all, trail, now));
        sections.add(coverageSummaryTable(coverage));
        sections.add(atRiskTable(coverage));
        sections.add(gapsTable(coverage));
        sections.add(methodology());

        return new ReportDocument(
                "Security posture report",
                "Comprehensive risk posture & audit summary",
                List.of(
                        provenance.generatedLine(now),
                        provenance.generatedByLine(actorEmail),
                        "Scope: " + p.total() + " findings - " + coverage.size() + " frameworks - "
                                + controlCount + " controls",
                        "Point-in-time snapshot of live data"),
                new ReportDocument.Headline(p.score(), band, bandKey),
                "Internal - for management and audit use. Contains security-sensitive findings.",
                List.copyOf(sections));
    }

    private ReportSection.Kpis kpis(PostureResponse p) {
        return new ReportSection.Kpis(List.of(
                new ReportSection.Kpis.Kpi("Total findings", String.valueOf(p.total()), "All recorded (excl. deleted)"),
                new ReportSection.Kpis.Kpi("Active findings", String.valueOf(p.active()), "Not yet resolved or closed"),
                new ReportSection.Kpis.Kpi("Critical active", String.valueOf(p.criticalActive()), "Highest-weight exposure"),
                new ReportSection.Kpis.Kpi("Remediated (90d)", String.valueOf(p.remediated90d()), "Closed in the last 90 days")));
    }

    private static List<ReportSection.BarChart.Bar> severityBars(PostureResponse p) {
        return p.bySeverity().stream()
                .map(s -> new ReportSection.BarChart.Bar(s.label(), s.count(), s.key()))
                .toList();
    }

    private static List<ReportSection.BarChart.Bar> statusBars(PostureResponse p) {
        return p.byStatus().stream()
                .map(s -> new ReportSection.BarChart.Bar(s.label(), s.count(), s.key()))
                .toList();
    }

    private ReportSection.Heatmap heatmap(PostureResponse p) {
        Map<String, String> statusLabels = p.byStatus().stream()
                .collect(Collectors.toMap(PostureResponse.StatusCount::key, PostureResponse.StatusCount::label));
        List<String> cols = p.heatStatuses().stream().map(k -> statusLabels.getOrDefault(k, k)).toList();
        List<ReportSection.Heatmap.Row> rows = p.heatRows().stream()
                .map(hr -> new ReportSection.Heatmap.Row(hr.label(), hr.key(), cells(hr, p.heatStatuses())))
                .toList();
        return new ReportSection.Heatmap("Risk concentration - severity × status",
                "Cell colour = inherent risk (severity × workflow state), independent of count.", cols, rows);
    }

    private static List<ReportSection.Heatmap.Cell> cells(PostureResponse.HeatRow hr, List<String> statuses) {
        List<ReportSection.Heatmap.Cell> cells = new ArrayList<>(hr.cells().size());
        for (int i = 0; i < hr.cells().size(); i++) {
            double intensity = SEV_WEIGHT.getOrDefault(hr.key(), 0)
                    * STATUS_FACTOR.getOrDefault(statuses.get(i), 0.0) / 4.0;
            cells.add(new ReportSection.Heatmap.Cell(hr.cells().get(i), (int) Math.round(intensity * 100)));
        }
        return cells;
    }

    private ReportSection.Insights insights(PostureResponse p, List<FindingSummary> all,
                                            Map<Framework, List<CoverageRow>> coverage, Instant now) {
        List<PostureInsights.CoverageStat> stats = coverage.entrySet().stream()
                .map(e -> new PostureInsights.CoverageStat(
                        frameworkName(e.getKey()),
                        e.getValue().size(),
                        e.getValue().stream().filter(r -> r.findingCount() > 0).count(),
                        e.getValue().stream().filter(CoverageRow::atRisk).count()))
                .toList();
        PostureInsights.Result result = PostureInsights.compose(p, all, stats, now);
        return new ReportSection.Insights("Key insights", result.bullets(), result.recommendations(), result.caveat());
    }

    private ReportSection.TableSection topRisksTable(List<FindingSummary> all, Instant now) {
        List<FindingSummary> top = topRisks(all, TOP_RISKS);
        long active = all.stream().filter(f -> ACTIVE_WIRE.contains(f.status())).count();
        List<List<String>> rows = top.stream().map(f -> List.of(
                f.reference(),
                f.title(),
                capitalize(f.severity()),
                FindingMapper.riskToReportCell(f.riskScore(), f.riskSource()),
                humanizeStatus(f.status()),
                f.owner(),
                f.createdAt() == null ? "" : String.valueOf(Duration.between(f.createdAt(), now).toDays()))).toList();
        return new ReportSection.TableSection(
                "Top risks - highest-scoring active findings",
                "Top " + top.size() + " of " + active + " active findings by effective risk score "
                        + "(CVSS when recorded, otherwise severity-derived - marked \"der\").",
                List.of(new Column("Reference", 1.1f), new Column("Title", 2.7f), new Column("Severity", 0.8f),
                        new Column("Risk", 0.8f), new Column("Status", 1.0f), new Column("Owner", 1.1f),
                        new Column("Age (days)", 0.7f)),
                rows);
    }

    /** Active findings sorted by effective risk score (desc), capped — package-private for unit tests. */
    static List<FindingSummary> topRisks(List<FindingSummary> all, int limit) {
        return all.stream()
                .filter(f -> ACTIVE_WIRE.contains(f.status()))
                .sorted(Comparator.comparing(FindingSummary::riskScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    private ReportSection.TableSection acceptancesTable(PostureResponse p, List<FindingSummary> all,
                                                        List<AuditLog> trail, Instant now) {
        // Acceptance provenance: the latest transition-to-ACCEPTED event per finding (who signed it off).
        Map<Long, AuditLog> acceptedBy = new HashMap<>();
        for (AuditLog event : trail) {
            if (event.getToStatus() == FindingStatus.ACCEPTED) {
                acceptedBy.merge(event.getFinding().getId(), event,
                        (a, b) -> a.getTimestamp().isAfter(b.getTimestamp()) ? a : b);
            }
        }
        List<List<String>> rows = all.stream()
                .filter(f -> "accepted".equals(f.status()))
                .map(f -> {
                    AuditLog event = acceptedBy.get(f.id());
                    return List.of(
                            f.reference(),
                            f.title(),
                            capitalize(f.severity()),
                            FindingMapper.riskToReportCell(f.riskScore(), f.riskSource()),
                            event == null ? "-" : event.getActor().getName(),
                            event == null ? "-" : DAY.format(event.getTimestamp()));
                })
                .toList();

        Instant since = now.minus(ACTIVITY_WINDOW);
        long approved = trail.stream().filter(e -> e.getToStatus() == FindingStatus.APPROVED
                && !e.getTimestamp().isBefore(since)).count();
        long returned = trail.stream().filter(e -> e.getToStatus() == FindingStatus.RETURNED
                && !e.getTimestamp().isBefore(since)).count();
        long awaiting = p.byStatus().stream().filter(s -> "submitted".equals(s.key()))
                .mapToLong(PostureResponse.StatusCount::count).findFirst().orElse(0);
        return new ReportSection.TableSection(
                "Risk acceptances & review activity",
                "Review activity, last 90 days: " + approved + " approved - " + returned + " returned - "
                        + awaiting + " currently awaiting review. Accepted risks below remain live exposure.",
                List.of(new Column("Reference", 1.1f), new Column("Title", 2.8f), new Column("Severity", 0.9f),
                        new Column("Risk", 0.7f), new Column("Accepted by", 1.3f), new Column("Accepted on", 1.0f)),
                rows);
    }

    private ReportSection.TableSection coverageSummaryTable(Map<Framework, List<CoverageRow>> coverage) {
        List<List<String>> rows = coverage.entrySet().stream().map(e -> {
            List<CoverageRow> cov = e.getValue();
            long covered = cov.stream().filter(r -> r.findingCount() > 0).count();
            long atRisk = cov.stream().filter(CoverageRow::atRisk).count();
            int pct = cov.isEmpty() ? 0 : Math.round(covered * 100f / cov.size());
            return List.of(
                    frameworkName(e.getKey()),
                    String.valueOf(cov.size()),
                    String.valueOf(covered),
                    pct + "%",
                    String.valueOf(atRisk),
                    String.valueOf(cov.size() - covered));
        }).toList();
        return new ReportSection.TableSection(
                "Control coverage by framework",
                "Covered = at least one finding mapped. At risk = an active high/critical finding is mapped.",
                List.of(new Column("Framework", 2.2f), new Column("Controls", 0.7f), new Column("Covered", 0.7f),
                        new Column("Coverage", 0.7f), new Column("At risk", 0.7f), new Column("Gaps", 0.6f)),
                rows);
    }

    private ReportSection.TableSection atRiskTable(Map<Framework, List<CoverageRow>> coverage) {
        List<List<String>> rows = new ArrayList<>();
        coverage.forEach((fw, cov) -> cov.stream()
                .filter(CoverageRow::atRisk)
                .forEach(r -> rows.add(List.of(
                        fw.getName(),
                        r.control().code(),
                        r.control().title(),
                        String.valueOf(r.findingCount()),
                        r.highestSeverity() == null ? "" : capitalize(r.highestSeverity())))));
        return new ReportSection.TableSection(
                "At-risk controls",
                "Controls mapped to at least one active high or critical finding - remediation priority.",
                List.of(new Column("Framework", 1.5f), new Column("Code", 0.9f), new Column("Control", 3.2f),
                        new Column("Findings", 0.7f), new Column("Highest severity", 1.0f)),
                rows);
    }

    private ReportSection.TableSection gapsTable(Map<Framework, List<CoverageRow>> coverage) {
        List<List<String>> rows = new ArrayList<>();
        coverage.forEach((fw, cov) -> cov.stream()
                .filter(r -> r.findingCount() == 0)
                .forEach(r -> rows.add(List.of(fw.getName(), r.control().code(), r.control().title()))));
        return new ReportSection.TableSection(
                "Coverage gaps - controls with no mapped findings",
                "A gap means no findings are mapped: the control is either genuinely unaffected or not yet "
                        + "assessed. A gap is NOT a compliance claim.",
                List.of(new Column("Framework", 1.5f), new Column("Code", 0.9f), new Column("Control", 3.9f)),
                rows);
    }

    private ReportSection.Paragraphs methodology() {
        return new ReportSection.Paragraphs("Methodology & definitions", List.of(
                "Posture score: a volume-sensitive weighted sum over active findings - Critical 10, High 6, "
                        + "Medium 3, Low 1 - normalised against a cap of " + postureService.cap()
                        + " weighted points, scaled to 0-100 and clamped. Higher is worse.",
                "Active findings: status Open, In progress, Submitted or Returned. Approved, Remediated and "
                        + "Accepted findings no longer add to the posture load.",
                "Effective risk score per finding: the CVSS base score when recorded, otherwise derived from "
                        + "severity (Critical 9.0, High 7.5, Medium 5.0, Low 2.0) - derived scores are marked "
                        + "\"der\" in the tables.",
                "Severity bands follow CVSS v3.x: Critical 9.0-10.0, High 7.0-8.9, Medium 4.0-6.9, Low 0.1-3.9.",
                "At-risk control: at least one active high or critical finding is mapped to it. Coverage gap: "
                        + "no findings mapped - the control is either unaffected or not yet assessed.",
                "Remediated (90 days): findings in status Remediated whose last update falls inside the window.",
                "This report is generated from live data at the timestamp on the cover. The per-finding audit "
                        + "trail (every status change, edit and mapping, including deleted findings) is available "
                        + "as the separate audit log export."));
    }

    // ---- helpers ----

    private static String frameworkName(Framework fw) {
        return fw.getVersion() == null || fw.getVersion().isBlank()
                ? fw.getName()
                : fw.getName() + " " + fw.getVersion();
    }

    static String bandLabel(int score) {
        return BANDS.stream().filter(b -> score < b.max()).findFirst()
                .orElse(BANDS.get(BANDS.size() - 1)).label();
    }

    private static String bandKey(int score) {
        return BANDS.stream().filter(b -> score < b.max()).findFirst()
                .orElse(BANDS.get(BANDS.size() - 1)).colorKey();
    }

    private static String capitalize(String value) {
        return value == null || value.isEmpty()
                ? ""
                : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    /** Kebab wire status → human label ("in-progress" → "In progress"). */
    private static String humanizeStatus(String wire) {
        return capitalize(wire.replace('-', ' '));
    }

    private static ReportFormat parseFormat(String format) {
        try {
            return ReportFormat.fromWire(format);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }
}
