package io.muzoo.ssc.controlmap.report;

/** A finished report ready for HTTP download: the suggested filename, its media type, and bytes. */
public record RenderedReport(String filename, String contentType, byte[] body) {
}
