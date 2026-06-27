package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.domain.Asset;
import io.muzoo.ssc.controlmap.domain.Control;
import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.domain.FindingControlMapping;
import io.muzoo.ssc.controlmap.domain.FindingStatus;
import io.muzoo.ssc.controlmap.domain.MappingSource;
import io.muzoo.ssc.controlmap.domain.Severity;
import io.muzoo.ssc.controlmap.domain.User;
import io.muzoo.ssc.controlmap.repository.ControlRepository;
import io.muzoo.ssc.controlmap.repository.FindingControlMappingRepository;
import io.muzoo.ssc.controlmap.repository.FindingRepository;
import io.muzoo.ssc.controlmap.repository.UserRepository;
import io.muzoo.ssc.controlmap.web.dto.AddMappingRequest;
import io.muzoo.ssc.controlmap.web.dto.AssetRequest;
import io.muzoo.ssc.controlmap.web.dto.ControlRef;
import io.muzoo.ssc.controlmap.web.dto.FindingDetail;
import io.muzoo.ssc.controlmap.web.dto.FindingRequest;
import io.muzoo.ssc.controlmap.web.dto.FindingSummary;
import io.muzoo.ssc.controlmap.web.dto.PagedResponse;
import io.muzoo.ssc.controlmap.web.dto.TransitionRequest;
import io.muzoo.ssc.controlmap.workflow.WorkflowAction;
import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read and write operations for findings: the dashboard list, detail, and create/edit/delete. */
@Service
@Transactional(readOnly = true)
public class FindingService {

    /** Findings may be edited/deleted only while the owning analyst is actively working them (§8). */
    private static final Set<FindingStatus> EDITABLE =
            Set.of(FindingStatus.OPEN, FindingStatus.IN_PROGRESS, FindingStatus.RETURNED);

    private final FindingRepository findings;
    private final FindingControlMappingRepository mappings;
    private final ControlRepository controls;
    private final UserRepository users;
    private final FindingMapper mapper;
    private final WorkflowStateMachine workflow;

    public FindingService(FindingRepository findings, FindingControlMappingRepository mappings,
                          ControlRepository controls, UserRepository users, FindingMapper mapper,
                          WorkflowStateMachine workflow) {
        this.findings = findings;
        this.mappings = mappings;
        this.controls = controls;
        this.users = users;
        this.mapper = mapper;
        this.workflow = workflow;
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

    /** All findings (no paging) as summaries, newest first — backs report export (#14). */
    public List<FindingSummary> listAllForExport() {
        List<Finding> all = findings.findAll(Sort.by(Sort.Direction.DESC, "updatedAt"));
        Map<Long, List<ControlRef>> controlsByFinding = loadControlRefs(all);
        return all.stream()
                .map(f -> mapper.toSummary(f, controlsByFinding.getOrDefault(f.getId(), List.of())))
                .toList();
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

    public FindingDetail get(Long id) {
        return mapper.toDetail(requireFinding(id), mappedControls(id));
    }

    @Transactional
    public FindingDetail create(FindingRequest request, String currentUserEmail) {
        User owner = currentUser(currentUserEmail);
        Finding finding = new Finding(nextReference(), request.title(), request.description(),
                resolveSeverity(request.severity(), request.cvss()), request.cvss(), owner, toAsset(request.asset()));
        Finding saved = findings.save(finding);
        return mapper.toDetail(saved, List.of());
    }

    @Transactional
    public FindingDetail update(Long id, FindingRequest request, String currentUserEmail) {
        Finding finding = requireFinding(id);
        requireOwner(finding, currentUserEmail);
        requireEditable(finding);

        finding.setTitle(request.title());
        finding.setDescription(request.description());
        finding.setSeverity(resolveSeverity(request.severity(), request.cvss()));
        finding.setCvssScore(request.cvss());
        finding.setAsset(toAsset(request.asset()));
        // §8: the first edit of an OPEN finding moves it to IN_PROGRESS.
        if (finding.getStatus() == FindingStatus.OPEN) {
            finding.setStatus(FindingStatus.IN_PROGRESS);
        }
        Finding saved = findings.save(finding);
        return mapper.toDetail(saved, mappedControls(id));
    }

    @Transactional
    public void delete(Long id, String currentUserEmail) {
        Finding finding = requireFinding(id);
        requireOwner(finding, currentUserEmail);
        requireEditable(finding);
        findings.delete(finding); // child mappings/audit rows cascade at the DB (ON DELETE CASCADE)
    }

    @Transactional
    public FindingDetail addControl(Long findingId, AddMappingRequest request, String currentUserEmail) {
        Finding finding = requireFinding(findingId);
        requireOwner(finding, currentUserEmail);
        requireEditable(finding);

        Control control = controls.findById(request.controlId())
                .orElseThrow(() -> new NotFoundException("Control not found: " + request.controlId()));
        if (mappings.existsByFinding_IdAndControl_Id(findingId, control.getId())) {
            throw new ConflictException("That control is already mapped to this finding.");
        }

        FindingControlMapping mapping = new FindingControlMapping(finding, control);
        if (request.source() != null && MappingSource.AI_SUGGESTED.name().equalsIgnoreCase(request.source())) {
            mapping.setSource(MappingSource.AI_SUGGESTED);
            mapping.setAiConfidence(request.aiConfidence());
            mapping.setAiRationale(request.aiRationale());
            mapping.setAiModel(request.aiModel());
        }
        mappings.save(mapping);
        return mapper.toDetail(finding, mappedControls(findingId));
    }

    @Transactional
    public FindingDetail removeControl(Long findingId, Long controlId, String currentUserEmail) {
        Finding finding = requireFinding(findingId);
        requireOwner(finding, currentUserEmail);
        requireEditable(finding);

        FindingControlMapping mapping = mappings.findByFinding_IdAndControl_Id(findingId, controlId)
                .orElseThrow(() -> new NotFoundException("That control is not mapped to this finding."));
        mappings.delete(mapping);
        return mapper.toDetail(finding, mappedControls(findingId));
    }

    /**
     * Performs a role-gated workflow transition (PLAN §8) through the {@link WorkflowStateMachine}:
     * the state machine validates legality, actor/role + separation of duties, the required comment,
     * and the submit guard, then yields the new status. (The audit-log entry is wired in chunk #16.)
     */
    @Transactional
    public FindingDetail transition(Long id, TransitionRequest request, String currentUserEmail) {
        WorkflowAction action = parseAction(request.action());
        Finding finding = requireFinding(id);
        User actor = currentUser(currentUserEmail);
        boolean actorIsOwner = finding.getOwner().getEmail().equalsIgnoreCase(currentUserEmail);
        long mappedControlCount = mappings.countByFinding_Id(id);

        FindingStatus target = workflow.next(
                finding.getStatus(), action, request.comment(), actorIsOwner, actor.getRole(), mappedControlCount);
        finding.setStatus(target);
        Finding saved = findings.save(finding);
        return mapper.toDetail(saved, mappedControls(id));
    }

    private WorkflowAction parseAction(String value) {
        try {
            return WorkflowAction.fromWire(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown action: " + value);
        }
    }

    private Finding requireFinding(Long id) {
        return findings.findById(id).orElseThrow(() -> new NotFoundException("Finding not found: " + id));
    }

    private void requireOwner(Finding finding, String email) {
        if (!finding.getOwner().getEmail().equalsIgnoreCase(email)) {
            throw new ForbiddenException("Only the owning analyst can modify this finding.");
        }
    }

    private void requireEditable(Finding finding) {
        if (!EDITABLE.contains(finding.getStatus())) {
            throw new ConflictException(
                    "This finding cannot be edited in its current state (" + FindingMapper.statusToWire(finding.getStatus()) + ").");
        }
    }

    private User currentUser(String email) {
        return users.findByEmail(email).orElseThrow(() -> new ForbiddenException("Authenticated user not found."));
    }

    private List<FindingDetail.MappedControl> mappedControls(Long findingId) {
        return mappings.findWithControlByFindingIds(List.of(findingId)).stream()
                .map(m -> new FindingDetail.MappedControl(
                        m.getControl().getId(), m.getControl().getFramework().getSlug(),
                        m.getControl().getCode(), m.getControl().getTitle()))
                .toList();
    }

    /** Severity is derived from CVSS when present (CVSS 3.x bands, §16 #6); otherwise it is required. */
    private Severity resolveSeverity(String severityWire, BigDecimal cvss) {
        if (cvss != null) {
            return bandFromCvss(cvss);
        }
        if (isBlank(severityWire)) {
            throw new BadRequestException("Severity is required when no CVSS score is provided.");
        }
        try {
            return FindingMapper.severityFromWire(severityWire);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid severity: " + severityWire);
        }
    }

    private static Severity bandFromCvss(BigDecimal cvss) {
        double v = cvss.doubleValue();
        if (v >= 9.0) return Severity.CRITICAL;
        if (v >= 7.0) return Severity.HIGH;
        if (v >= 4.0) return Severity.MEDIUM;
        return Severity.LOW;
    }

    private static Asset toAsset(AssetRequest a) {
        return new Asset(a.name(), blankToNull(a.env()), blankToNull(a.component()), blankToNull(a.url()));
    }

    /** Next human reference for the current year: CM-YYYY-NNNN (zero-padded, increasing). */
    private String nextReference() {
        String prefix = "CM-" + Year.now().getValue() + "-";
        int next = findings.findFirstByReferenceStartingWithOrderByReferenceDesc(prefix)
                .map(f -> sequenceOf(f.getReference()) + 1)
                .orElse(1);
        return String.format("%s%04d", prefix, next);
    }

    private static int sequenceOf(String reference) {
        try {
            return Integer.parseInt(reference.substring(reference.lastIndexOf('-') + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
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
