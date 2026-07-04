package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.domain.Control;
import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.domain.FindingStatus;
import io.muzoo.ssc.controlmap.domain.Severity;
import io.muzoo.ssc.controlmap.repository.ControlRepository;
import io.muzoo.ssc.controlmap.repository.FindingControlMappingRepository;
import io.muzoo.ssc.controlmap.repository.FrameworkRepository;
import io.muzoo.ssc.controlmap.web.dto.CoverageRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coverage rollup (PLAN §10/§13): for a framework, every control with its mapped-finding count, the
 * worst severity among those findings, and an at-risk flag. Read-only aggregation done in the service
 * (the catalog is small) rather than in SQL, keeping the enum-band logic in one readable place.
 */
@Service
@Transactional(readOnly = true)
public class CoverageService {

    /**
     * "Active" findings per PLAN §9 — not yet resolved/closed; APPROVED/REMEDIATED/ACCEPTED drop out.
     * A control is at-risk only when an active high/critical finding still maps to it.
     */
    private static final Set<FindingStatus> ACTIVE = EnumSet.of(
            FindingStatus.OPEN, FindingStatus.IN_PROGRESS, FindingStatus.SUBMITTED, FindingStatus.RETURNED);

    private final FrameworkRepository frameworks;
    private final ControlRepository controls;
    private final FindingControlMappingRepository mappings;

    public CoverageService(FrameworkRepository frameworks, ControlRepository controls,
                           FindingControlMappingRepository mappings) {
        this.frameworks = frameworks;
        this.controls = controls;
        this.mappings = mappings;
    }

    /** Coverage rows for a framework (by slug), in catalog order. Unknown slug → 404. */
    public List<CoverageRow> coverage(String frameworkSlug) {
        if (frameworks.findBySlug(frameworkSlug).isEmpty()) {
            throw new NotFoundException("Unknown framework: " + frameworkSlug);
        }
        Map<Long, List<Finding>> findingsByControl = new HashMap<>();
        for (var mapping : mappings.findWithFindingByFrameworkSlug(frameworkSlug)) {
            findingsByControl.computeIfAbsent(mapping.getControl().getId(), k -> new ArrayList<>())
                    .add(mapping.getFinding());
        }
        // Natural code order (A.5.2 before A.5.10) so the grid is stable regardless of seed/insertion
        // order — matches the Control Catalog screen (see CatalogService).
        return controls.findByFramework_SlugOrderByIdAsc(frameworkSlug).stream()
                .sorted(Comparator.comparing(Control::getCode, CatalogService::compareNatural))
                .map(control -> toRow(control, findingsByControl.getOrDefault(control.getId(), List.of())))
                .toList();
    }

    private CoverageRow toRow(Control control, List<Finding> mapped) {
        Severity highest = mapped.stream()
                .map(Finding::getSeverity)
                .min(Comparator.comparingInt(Enum::ordinal)) // CRITICAL is ordinal 0 → most severe
                .orElse(null);
        boolean atRisk = mapped.stream().anyMatch(f -> ACTIVE.contains(f.getStatus())
                && (f.getSeverity() == Severity.CRITICAL || f.getSeverity() == Severity.HIGH));
        return new CoverageRow(
                new CoverageRow.ControlInfo(control.getId(), control.getCode(), control.getTitle()),
                mapped.size(),
                highest == null ? null : FindingMapper.severityToWire(highest),
                atRisk);
    }
}
