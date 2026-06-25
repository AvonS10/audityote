package io.muzoo.ssc.controlmap.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Full finding detail (PLAN §10) returned by create / get / update. Severity is lowercase and status
 * kebab-case (the Mapper translates the enums); {@code controls} are the mapped controls.
 */
public record FindingDetail(
        Long id,
        String reference,
        String title,
        String description,
        String severity,
        BigDecimal cvss,
        String status,
        AssetDto asset,
        String owner,
        Instant createdAt,
        Instant updatedAt,
        List<MappedControl> controls) {

    /** The affected asset. */
    public record AssetDto(String name, String env, String component, String url) {
    }

    /** A control mapped to the finding (carries the control id for the remove-mapping action, #12). */
    public record MappedControl(Long controlId, String framework, String code, String title) {
    }
}
