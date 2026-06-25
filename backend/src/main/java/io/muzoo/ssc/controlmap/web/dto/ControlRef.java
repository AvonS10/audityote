package io.muzoo.ssc.controlmap.web.dto;

/** A lightweight reference to a mapped control (framework slug + code) for list/tag rendering. */
public record ControlRef(String framework, String code) {
}
