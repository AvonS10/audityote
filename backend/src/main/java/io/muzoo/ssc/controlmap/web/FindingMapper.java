package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.domain.FindingStatus;
import io.muzoo.ssc.controlmap.domain.Severity;
import io.muzoo.ssc.controlmap.web.dto.ControlRef;
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

    public FindingSummary toSummary(Finding finding, List<ControlRef> controls) {
        return new FindingSummary(
                finding.getId(),
                finding.getReference(),
                finding.getTitle(),
                finding.getAsset() != null ? finding.getAsset().getName() : null,
                severityToWire(finding.getSeverity()),
                finding.getCvssScore(),
                statusToWire(finding.getStatus()),
                controls,
                finding.getOwner().getName(),
                finding.getUpdatedAt());
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
