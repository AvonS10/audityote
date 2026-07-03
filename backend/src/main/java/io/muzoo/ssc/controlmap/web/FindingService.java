package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.audit.FindingAuditEvent;
import io.muzoo.ssc.controlmap.domain.Asset;
import io.muzoo.ssc.controlmap.domain.Control;
import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.domain.FindingControlMapping;
import io.muzoo.ssc.controlmap.domain.FindingStatus;
import io.muzoo.ssc.controlmap.domain.MappingSource;
import io.muzoo.ssc.controlmap.domain.Severity;
import io.muzoo.ssc.controlmap.domain.User;
import io.muzoo.ssc.controlmap.repository.AuditLogRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
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
    private final AuditLogRepository auditLog;
    private final FindingMapper mapper;
    private final WorkflowStateMachine workflow;
    private final ApplicationEventPublisher events;

    public FindingService(FindingRepository findings, FindingControlMappingRepository mappings,
                          ControlRepository controls, UserRepository users, AuditLogRepository auditLog,
                          FindingMapper mapper, WorkflowStateMachine workflow, ApplicationEventPublisher events) {
        this.findings = findings;
        this.mappings = mappings;
        this.controls = controls;
        this.users = users;
        this.auditLog = auditLog;
        this.mapper = mapper;
        this.workflow = workflow;
        this.events = events;
    }

    public PagedResponse<FindingSummary> list(String status, String severity, String framework, String q,
                                              boolean deleted, boolean mine, String currentUserEmail,
                                              int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        // "Returned to me" / mine filter: restrict to the caller's own findings (#4).
        String ownerEmail = mine ? currentUserEmail : null;
        Page<Finding> result = findings.search(
                parseStatus(status), parseSeverity(severity), ownerEmail, normalizeQuery(q),
                blankToNull(framework), deleted, pageable);

        Map<Long, List<ControlRef>> controlsByFinding = loadControlRefs(result.getContent());
        List<FindingSummary> summaries = result.getContent().stream()
                .map(f -> mapper.toSummary(f, controlsByFinding.getOrDefault(f.getId(), List.of())))
                .toList();
        return PagedResponse.of(result, summaries);
    }

    /**
     * Findings awaiting reviewer sign-off: {@code SUBMITTED} and active, oldest-submitted first so the
     * queue reads FIFO (longest-waiting at the top). Backs {@code GET /api/reviews/queue} (#17); the
     * approve/return decisions themselves go through {@link #transition} (the State machine, §8).
     */
    public List<FindingSummary> reviewQueue() {
        List<Finding> submitted = findings.findByStatusWithOwner(
                FindingStatus.SUBMITTED, Sort.by(Sort.Direction.ASC, "updatedAt"));
        Map<Long, List<ControlRef>> controlsByFinding = loadControlRefs(submitted);
        return submitted.stream()
                .map(f -> mapper.toSummary(f, controlsByFinding.getOrDefault(f.getId(), List.of())))
                .toList();
    }

    /** All active findings (no paging) as summaries, newest first — backs report export (#14). */
    public List<FindingSummary> listAllForExport() {
        List<Finding> all = findings.findByDeletedAtIsNull(Sort.by(Sort.Direction.DESC, "updatedAt"));
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

    /** Detail view — serves soft-deleted findings too (read-only), so their audit trail stays reviewable. */
    public FindingDetail get(Long id) {
        return detail(requireExistingFinding(id));
    }

    @Transactional
    public FindingDetail create(FindingRequest request, String currentUserEmail) {
        User owner = currentUser(currentUserEmail);
        Finding finding = new Finding(nextReference(), request.title(), request.description(),
                resolveSeverity(request.severity(), request.cvss()), request.cvss(), owner, toAsset(request.asset()));
        Finding saved = findings.save(finding);
        events.publishEvent(new FindingAuditEvent(saved, owner, "created", null, FindingStatus.OPEN, null));
        return detail(saved);
    }

    @Transactional
    public FindingDetail update(Long id, FindingRequest request, String currentUserEmail) {
        Finding finding = requireFinding(id);
        requireOwner(finding, currentUserEmail);
        requireEditable(finding);

        // Snapshot before applying, so we can record exactly what changed.
        String oldTitle = finding.getTitle();
        String oldDescription = finding.getDescription();
        Severity oldSeverity = finding.getSeverity();
        BigDecimal oldCvss = finding.getCvssScore();
        Asset oldAsset = finding.getAsset();
        FindingStatus oldStatus = finding.getStatus();

        Severity newSeverity = resolveSeverity(request.severity(), request.cvss());
        Asset newAsset = toAsset(request.asset());
        finding.setTitle(request.title());
        finding.setDescription(request.description());
        finding.setSeverity(newSeverity);
        finding.setCvssScore(request.cvss());
        finding.setAsset(newAsset);
        // §8: the first edit of an OPEN finding moves it to IN_PROGRESS.
        if (finding.getStatus() == FindingStatus.OPEN) {
            finding.setStatus(FindingStatus.IN_PROGRESS);
        }
        Finding saved = findings.save(finding);

        String summary = editSummary(oldTitle, oldDescription, oldSeverity, oldCvss, oldAsset, finding);
        boolean statusChanged = oldStatus != finding.getStatus();
        if (!summary.isEmpty() || statusChanged) {
            events.publishEvent(new FindingAuditEvent(saved, finding.getOwner(), "edited",
                    statusChanged ? oldStatus : null, statusChanged ? finding.getStatus() : null,
                    summary.isEmpty() ? null : summary));
        }
        return detail(saved);
    }

    @Transactional
    public void delete(Long id, String currentUserEmail) {
        Finding finding = requireFinding(id);
        requireOwner(finding, currentUserEmail);
        requireEditable(finding);
        // Soft delete: retain the row (and its mappings + audit trail) and record the deletion.
        finding.markDeleted();
        Finding saved = findings.save(finding);
        events.publishEvent(new FindingAuditEvent(saved, finding.getOwner(), "deleted", null, null, null));
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

        // Manual mapping only — this path never sets AI provenance. AI-suggested mappings have exactly
        // one door: addAiMapping (below), reached solely via the accept-suggestion flow (S2b), which
        // stamps provenance server-side. That is what keeps the audit trail from being spoofed.
        mappings.save(new FindingControlMapping(finding, control));
        events.publishEvent(new FindingAuditEvent(finding, finding.getOwner(), "mapped", null, null, controlRef(control)));
        return detail(finding);
    }

    /**
     * Creates an AI-provenance mapping — reached only through the accept-suggestion flow (S2b). Unlike
     * {@link #addControl}, the provenance ({@code confidence}/{@code rationale}/{@code model}) is supplied
     * by the server from the cached suggestion it produced, never by the client, so a mapping can't be
     * dishonestly labelled AI-suggested. The audit note records the AI origin (Observer).
     */
    @Transactional
    public FindingDetail addAiMapping(Long findingId, Long controlId, Double confidence, String rationale,
                                      String model, String currentUserEmail) {
        Finding finding = requireFinding(findingId);
        requireOwner(finding, currentUserEmail);
        requireEditable(finding);

        Control control = controls.findById(controlId)
                .orElseThrow(() -> new NotFoundException("Control not found: " + controlId));
        if (mappings.existsByFinding_IdAndControl_Id(findingId, control.getId())) {
            throw new ConflictException("That control is already mapped to this finding.");
        }

        FindingControlMapping mapping = new FindingControlMapping(finding, control);
        mapping.setSource(MappingSource.AI_SUGGESTED);
        mapping.setAiConfidence(confidence);
        mapping.setAiRationale(rationale);
        mapping.setAiModel(model);
        mappings.save(mapping);
        events.publishEvent(new FindingAuditEvent(finding, finding.getOwner(), "mapped", null, null,
                controlRef(control) + " (AI-suggested)"));
        return detail(finding);
    }

    @Transactional
    public FindingDetail removeControl(Long findingId, Long controlId, String currentUserEmail) {
        Finding finding = requireFinding(findingId);
        requireOwner(finding, currentUserEmail);
        requireEditable(finding);

        FindingControlMapping mapping = mappings.findByFinding_IdAndControl_Id(findingId, controlId)
                .orElseThrow(() -> new NotFoundException("That control is not mapped to this finding."));
        String ref = controlRef(mapping.getControl());
        mappings.delete(mapping);
        events.publishEvent(new FindingAuditEvent(finding, finding.getOwner(), "unmapped", null, null, ref));
        return detail(finding);
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

        FindingStatus from = finding.getStatus();
        FindingStatus target = workflow.next(
                from, action, request.comment(), actorIsOwner, actor.getRole(), mappedControlCount);
        finding.setStatus(target);
        Finding saved = findings.save(finding);
        events.publishEvent(new FindingAuditEvent(saved, actor, action.wire(), from, target, blankToNull(request.comment())));
        return detail(saved);
    }

    private WorkflowAction parseAction(String value) {
        try {
            return WorkflowAction.fromWire(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown action: " + value);
        }
    }

    /** Loads a finding for mutation: a soft-deleted finding is treated as gone (404). */
    /**
     * Loads a finding the caller may request AI control suggestions for (S2): it must exist, be owned by
     * the caller, and be in an editable state — the same gate as adding a mapping, since a suggestion is
     * only useful on a finding you can still map. Reused by {@link MappingSuggestionService}; runs in this
     * service's read transaction so the returned finding stays managed for lazy access.
     */
    public Finding requireSuggestable(Long id, String callerEmail) {
        Finding finding = requireFinding(id);
        requireOwner(finding, callerEmail);
        requireEditable(finding);
        return finding;
    }

    private Finding requireFinding(Long id) {
        Finding finding = requireExistingFinding(id);
        if (finding.isDeleted()) {
            throw new NotFoundException("Finding not found: " + id);
        }
        return finding;
    }

    /** Loads a finding regardless of soft-delete state — for the read-only detail view. */
    private Finding requireExistingFinding(Long id) {
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

    /** Full detail DTO: the finding with its mapped controls and audit trail. */
    private FindingDetail detail(Finding finding) {
        Long id = finding.getId();
        return mapper.toDetail(finding, mappedControls(id), auditTrail(id));
    }

    private List<FindingDetail.MappedControl> mappedControls(Long findingId) {
        return mappings.findWithControlByFindingIds(List.of(findingId)).stream()
                .map(m -> new FindingDetail.MappedControl(
                        m.getControl().getId(), m.getControl().getFramework().getSlug(),
                        m.getControl().getCode(), m.getControl().getTitle()))
                .toList();
    }

    private List<FindingDetail.AuditEntry> auditTrail(Long findingId) {
        return auditLog.findByFinding_IdOrderByTimestampAscIdAsc(findingId).stream()
                .map(mapper::toAuditEntry)
                .toList();
    }

    /** Human reference for an audited mapping change, e.g. {@code owasp:A05 — Security misconfiguration}. */
    private static String controlRef(Control control) {
        return control.getFramework().getSlug() + ":" + control.getCode() + " — " + control.getTitle();
    }

    /** Summarises what an edit changed (for the audit comment); severity/CVSS show their old→new values. */
    private static String editSummary(String oldTitle, String oldDescription, Severity oldSeverity,
                                      BigDecimal oldCvss, Asset oldAsset, Finding now) {
        List<String> changes = new ArrayList<>();
        if (!Objects.equals(oldTitle, now.getTitle())) {
            changes.add("title");
        }
        if (!Objects.equals(oldDescription, now.getDescription())) {
            changes.add("description");
        }
        if (oldSeverity != now.getSeverity()) {
            changes.add("severity " + FindingMapper.severityToWire(oldSeverity) + "→" + FindingMapper.severityToWire(now.getSeverity()));
        }
        if (!cvssEqual(oldCvss, now.getCvssScore())) {
            changes.add("CVSS " + cvssText(oldCvss) + "→" + cvssText(now.getCvssScore()));
        }
        if (assetChanged(oldAsset, now.getAsset())) {
            changes.add("asset");
        }
        return String.join(", ", changes);
    }

    private static boolean cvssEqual(BigDecimal a, BigDecimal b) {
        return (a == null || b == null) ? a == b : a.compareTo(b) == 0;
    }

    private static String cvssText(BigDecimal value) {
        return value == null ? "none" : value.toPlainString();
    }

    private static boolean assetChanged(Asset a, Asset b) {
        if (a == null || b == null) {
            return a != b;
        }
        return !Objects.equals(a.getName(), b.getName())
                || !Objects.equals(a.getEnv(), b.getEnv())
                || !Objects.equals(a.getComponent(), b.getComponent())
                || !Objects.equals(a.getUrl(), b.getUrl());
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
