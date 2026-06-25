package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.domain.Control;
import io.muzoo.ssc.controlmap.repository.ControlRepository;
import io.muzoo.ssc.controlmap.repository.FrameworkRepository;
import io.muzoo.ssc.controlmap.web.dto.ControlResponse;
import io.muzoo.ssc.controlmap.web.dto.FrameworkResponse;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only catalog queries: frameworks and their controls (with optional text search). */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final FrameworkRepository frameworks;
    private final ControlRepository controls;
    private final CatalogMapper mapper;

    public CatalogService(FrameworkRepository frameworks, ControlRepository controls, CatalogMapper mapper) {
        this.frameworks = frameworks;
        this.controls = controls;
        this.mapper = mapper;
    }

    public List<FrameworkResponse> listFrameworks() {
        return frameworks.findAllByOrderByNameAsc().stream().map(mapper::toFrameworkResponse).toList();
    }

    /** Controls of a framework (by slug), optionally filtered by a case-insensitive text query. */
    public List<ControlResponse> listControls(String frameworkSlug, String query) {
        if (frameworks.findBySlug(frameworkSlug).isEmpty()) {
            throw new NotFoundException("Unknown framework: " + frameworkSlug);
        }
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return controls.findByFramework_SlugOrderByIdAsc(frameworkSlug).stream()
                .filter(c -> q.isEmpty() || matches(c, q))
                .map(mapper::toControlResponse)
                .toList();
    }

    private static boolean matches(Control c, String q) {
        return contains(c.getCode(), q)
                || contains(c.getTitle(), q)
                || contains(c.getDescription(), q)
                || contains(c.getCategory(), q);
    }

    private static boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
    }
}
