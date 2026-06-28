package io.muzoo.ssc.controlmap.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A finding as shown in the dashboard table (PLAN §10). Severity is lowercase and status is
 * kebab-case over the wire (the Mapper translates from the JPA enums); {@code controls} lists the
 * mapped controls for the framework tags.
 */
public record FindingSummary(
        Long id,
        String reference,
        String title,
        String asset,
        String severity,
        BigDecimal cvss,
        BigDecimal riskScore,
        String riskSource,
        String status,
        List<ControlRef> controls,
        String owner,
        Instant updatedAt) {
}
