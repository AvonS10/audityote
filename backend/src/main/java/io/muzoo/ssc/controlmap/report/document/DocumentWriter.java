package io.muzoo.ssc.controlmap.report.document;

import io.muzoo.ssc.controlmap.report.ReportFormat;

/**
 * Serializes a {@link ReportDocument} into the bytes of one {@link ReportFormat} — the document
 * counterpart of {@code ReportWriter} (which stays the contract for flat tabular reports). Each
 * implementation is a Spring bean declaring its format; the {@code ReportFactory} indexes document
 * writers the same way it indexes tabular ones, so a new format is still just a new bean (OCP).
 */
public interface DocumentWriter {

    ReportFormat format();

    byte[] write(ReportDocument document);
}
