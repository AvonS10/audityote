package io.muzoo.ssc.controlmap.report;

/**
 * Serializes {@link ReportData} into the bytes of one {@link ReportFormat}. Each implementation is a
 * Spring bean declaring the format it handles; the {@link ReportFactory} indexes them by format, so
 * adding a new format (e.g. PDF) is a new bean — no change to the factory or callers (OCP, DIP).
 */
public interface ReportWriter {

    ReportFormat format();

    byte[] write(ReportData data);
}
