package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.domain.Control;
import io.muzoo.ssc.controlmap.repository.ControlRepository;
import io.muzoo.ssc.controlmap.repository.FrameworkRepository;
import io.muzoo.ssc.controlmap.web.dto.ControlResponse;
import io.muzoo.ssc.controlmap.web.dto.FrameworkResponse;
import java.util.Comparator;
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
        // Order by a natural sort of the code (A.5.2 before A.5.10, A01 before A10) rather than by
        // insertion id. This keeps the catalog screen grouped and correctly ordered regardless of the
        // order controls were seeded in — important once a framework is expanded on a database that
        // already held the earlier, smaller set (its original controls keep their lower ids).
        return controls.findByFramework_SlugOrderByIdAsc(frameworkSlug).stream()
                .filter(c -> q.isEmpty() || matches(c, q))
                .sorted(Comparator.comparing(Control::getCode, CatalogService::compareNatural))
                .map(mapper::toControlResponse)
                .toList();
    }

    /**
     * Compares two control codes in "natural" order: digit runs compare by numeric value, other
     * characters lexicographically. So {@code A.5.2} precedes {@code A.5.10} and {@code A01} precedes
     * {@code A10}, instead of the plain string order that would interleave them.
     */
    static int compareNatural(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int startA = i;
                int startB = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) {
                    i++;
                }
                while (j < b.length() && Character.isDigit(b.charAt(j))) {
                    j++;
                }
                String na = stripLeadingZeros(a.substring(startA, i));
                String nb = stripLeadingZeros(b.substring(startB, j));
                if (na.length() != nb.length()) {
                    return na.length() - nb.length();
                }
                int cmp = na.compareTo(nb);
                if (cmp != 0) {
                    return cmp;
                }
            } else {
                if (ca != cb) {
                    return Character.compare(ca, cb);
                }
                i++;
                j++;
            }
        }
        return (a.length() - i) - (b.length() - j);
    }

    private static String stripLeadingZeros(String digits) {
        int k = 0;
        while (k < digits.length() - 1 && digits.charAt(k) == '0') {
            k++;
        }
        return digits.substring(k);
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
