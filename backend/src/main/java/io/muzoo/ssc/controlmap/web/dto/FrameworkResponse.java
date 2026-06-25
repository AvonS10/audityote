package io.muzoo.ssc.controlmap.web.dto;

/** A framework as exposed by the catalog API (PLAN §10): the slug is the key the UI references. */
public record FrameworkResponse(Long id, String slug, String name, String version) {
}
