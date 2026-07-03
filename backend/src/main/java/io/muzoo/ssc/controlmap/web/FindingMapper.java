package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.domain.Asset;
import io.muzoo.ssc.controlmap.domain.AuditLog;
import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.domain.FindingStatus;
import io.muzoo.ssc.controlmap.domain.Severity;
import io.muzoo.ssc.controlmap.risk.RiskScore;
import io.muzoo.ssc.controlmap.risk.RiskScoringService;
import io.muzoo.ssc.controlmap.web.dto.ControlRef;
import io.muzoo.ssc.controlmap.web.dto.FindingDetail;
import io.muzoo.ssc.controlmap.web.dto.FindingSummary;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Maps {@link Finding} entities to API DTOs and owns the casing translation between the JPA enums
 * and the frontend wire format: severity is lowercase ({@code critical}) and status is kebab-case
 * ({@code in-progress}). The raw enum is never exposed to the client (PLAN §3/§10).
 */
@Component
public class FindingMapper {

    private final RiskScoringService riskScoring;

    public FindingMapper(RiskScoringService riskScoring) {
        this.riskScoring = riskScoring;
    }

    public FindingSummary toSummary(Finding finding, List<ControlRef> controls) {
        RiskScore risk = riskScoring.score(finding);
        return new FindingSummary(
                finding.getId(),
                finding.getReference(),
                finding.getTitle(),
                finding.getAsset() != null ? finding.getAsset().getName() : null,
                severityToWire(finding.getSeverity()),
                finding.getCvssScore(),
                risk.value(),
                risk.source().wire(),
                statusToWire(finding.getStatus()),
                controls,
                finding.getOwner().getName(),
                finding.getCreatedAt(),
                finding.getUpdatedAt());
    }

    public FindingDetail toDetail(Finding finding, List<FindingDetail.MappedControl> controls,
                                  List<FindingDetail.AuditEntry> audit) {
        Asset asset = finding.getAsset();
        RiskScore risk = riskScoring.score(finding);
        return new FindingDetail(
                finding.getId(),
                finding.getReference(),
                finding.getTitle(),
                finding.getDescription(),
                severityToWire(finding.getSeverity()),
                finding.getCvssScore(),
                risk.value(),
                risk.source().wire(),
                statusToWire(finding.getStatus()),
                asset == null ? null : new FindingDetail.AssetDto(asset.getName(), asset.getEnv(), asset.getComponent(), asset.getUrl()),
                finding.getOwner().getName(),
                finding.getCreatedAt(),
                finding.getUpdatedAt(),
                finding.isDeleted(),
                controls,
                audit);
    }

    public FindingDetail.AuditEntry toAuditEntry(AuditLog entry) {
        return new FindingDetail.AuditEntry(
                entry.getActor().getName(),
                entry.getAction(),
                entry.getFromStatus() == null ? null : statusToWire(entry.getFromStatus()),
                entry.getToStatus() == null ? null : statusToWire(entry.getToStatus()),
                entry.getComment(),
                entry.getTimestamp());
    }

    // ---- casing translation (enum <-> wire) ----

    public static String severityToWire(Severity severity) {
        return severity.name().toLowerCase(Locale.ROOT);
    }

    public static Severity severityFromWire(String value) {
        return Severity.valueOf(value.toUpperCase(Locale.ROOT));
    }

    public static String statusToWire(FindingStatus status) {
        return status.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static FindingStatus statusFromWire(String value) {
        return FindingStatus.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
