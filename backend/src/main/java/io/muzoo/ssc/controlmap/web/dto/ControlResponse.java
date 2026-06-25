package io.muzoo.ssc.controlmap.web.dto;

/**
 * A control as exposed by the catalog API (PLAN §10). {@code framework} is the framework slug;
 * {@code category} is the theme group (may be null) used to organise the catalog screen.
 */
public record ControlResponse(
        Long id,
        String framework,
        String code,
        String title,
        String description,
        String category) {
}
