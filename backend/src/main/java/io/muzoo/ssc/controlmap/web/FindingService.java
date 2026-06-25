package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.domain.FindingStatus;
import io.muzoo.ssc.controlmap.domain.Severity;
import io.muzoo.ssc.controlmap.repository.FindingControlMappingRepository;
import io.muzoo.ssc.controlmap.repository.FindingRepository;
import io.muzoo.ssc.controlmap.web.dto.ControlRef;
import io.muzoo.ssc.controlmap.web.dto.FindingSummary;
import io.muzoo.ssc.controlmap.web.dto.PagedResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read queries for findings: the filtered, paged dashboard list. */
@Service
@Transactional(readOnly = true)
public class FindingService {

    private final FindingRepository findings;
    private final FindingControlMappingRepository mappings;
    private final FindingMapper mapper;

    public FindingService(FindingRepository findings, FindingControlMappingRepository mappings, FindingMapper mapper) {
        this.findings = findings;
        this.mappings = mappings;
        this.mapper = mapper;
    }

    public PagedResponse<FindingSummary> list(String status, String severity, String framework, String q,
                                              int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<Finding> result = findings.search(
                parseStatus(status), parseSeverity(severity), normalizeQuery(q), blankToNull(framework), pageable);

        Map<Long, List<ControlRef>> controlsByFinding = loadControlRefs(result.getContent());
        List<FindingSummary> summaries = result.getContent().stream()
                .map(f -> mapper.toSummary(f, controlsByFinding.getOrDefault(f.getId(), List.of())))
                .toList();
        return PagedResponse.of(result, summaries);
    }

    /** One batch query for all mapped controls on the page, grouped by finding id (no N+1). */
    private Map<Long, List<ControlRef>> loadControlRefs(List<Finding> page) {
        if (page.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = page.stream().map(Finding::getId).toList();
        return mappings.findWithControlByFindingIds(ids).stream()
                .collect(Collectors.groupingBy(
                        m -> m.getFinding().getId(),
                        Collectors.mapping(
                                m -> new ControlRef(m.getControl().getFramework().getSlug(), m.getControl().getCode()),
                                Collectors.toList())));
    }

    private FindingStatus parseStatus(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return FindingMapper.statusFromWire(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status: " + value);
        }
    }

    private Severity parseSeverity(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return FindingMapper.severityFromWire(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid severity: " + value);
        }
    }

    /** Search term is never null — empty string means "no text filter" (matches the JPQL `:q = ''`). */
    private static String normalizeQuery(String value) {
        return isBlank(value) ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
