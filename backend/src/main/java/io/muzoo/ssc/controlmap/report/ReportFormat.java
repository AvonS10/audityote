package io.muzoo.ssc.controlmap.report;

import java.util.Locale;

/**
 * Supported report output formats (PLAN §10/§14). Each carries the file extension and HTTP media
 * type used for the download. CSV ships first; PDF is added in the next slice (a new enum constant +
 * a {@link ReportWriter} bean — the {@link ReportFactory} needs no change, per OCP).
 */
public enum ReportFormat {
    CSV("csv", "text/csv");

    private final String extension;
    private final String mediaType;

    ReportFormat(String extension, String mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    public String extension() {
        return extension;
    }

    public String mediaType() {
        return mediaType;
    }

    /** Resolves the {@code ?format=} query value; unknown/unsupported formats throw. */
    public static ReportFormat fromWire(String value) {
        String v = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (ReportFormat f : values()) {
            if (f.extension.equals(v)) {
                return f;
            }
        }
        throw new IllegalArgumentException("Unsupported report format: " + value);
    }
}
