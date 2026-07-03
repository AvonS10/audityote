package io.muzoo.ssc.controlmap.report;

import io.muzoo.ssc.controlmap.report.document.DocumentWriter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Factory (PLAN §11) selecting the writer for a requested {@link ReportFormat} — {@link ReportWriter}
 * for flat tabular reports (findings/coverage/audit registers) and {@link DocumentWriter} for
 * multi-section documents (the posture report). Spring injects every writer bean and the factory
 * indexes them by format, so registering a new format or report family is just adding a bean — the
 * factory and its callers stay closed for modification (OCP).
 */
@Component
public class ReportFactory {

    private final Map<ReportFormat, ReportWriter> writers = new EnumMap<>(ReportFormat.class);
    private final Map<ReportFormat, DocumentWriter> documentWriters = new EnumMap<>(ReportFormat.class);

    public ReportFactory(List<ReportWriter> writerBeans, List<DocumentWriter> documentWriterBeans) {
        for (ReportWriter writer : writerBeans) {
            this.writers.put(writer.format(), writer);
        }
        for (DocumentWriter writer : documentWriterBeans) {
            this.documentWriters.put(writer.format(), writer);
        }
    }

    public ReportWriter writerFor(ReportFormat format) {
        ReportWriter writer = writers.get(format);
        if (writer == null) {
            throw new IllegalArgumentException("No writer registered for report format: " + format);
        }
        return writer;
    }

    public DocumentWriter documentWriterFor(ReportFormat format) {
        DocumentWriter writer = documentWriters.get(format);
        if (writer == null) {
            throw new IllegalArgumentException("No document writer registered for report format: " + format);
        }
        return writer;
    }
}
