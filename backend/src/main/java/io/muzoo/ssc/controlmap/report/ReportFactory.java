package io.muzoo.ssc.controlmap.report;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Factory (PLAN §11) selecting the {@link ReportWriter} for a requested {@link ReportFormat}. Spring
 * injects every writer bean and the factory indexes them by format, so registering a new format is
 * just adding a bean — the factory and its callers stay closed for modification (OCP).
 */
@Component
public class ReportFactory {

    private final Map<ReportFormat, ReportWriter> writers = new EnumMap<>(ReportFormat.class);

    public ReportFactory(List<ReportWriter> writerBeans) {
        for (ReportWriter writer : writerBeans) {
            this.writers.put(writer.format(), writer);
        }
    }

    public ReportWriter writerFor(ReportFormat format) {
        ReportWriter writer = writers.get(format);
        if (writer == null) {
            throw new IllegalArgumentException("No writer registered for report format: " + format);
        }
        return writer;
    }
}
