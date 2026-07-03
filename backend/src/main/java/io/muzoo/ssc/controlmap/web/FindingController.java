package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.web.dto.AcceptSuggestionRequest;
import io.muzoo.ssc.controlmap.web.dto.AddMappingRequest;
import io.muzoo.ssc.controlmap.web.dto.FindingDetail;
import io.muzoo.ssc.controlmap.web.dto.FindingRequest;
import io.muzoo.ssc.controlmap.web.dto.FindingSummary;
import io.muzoo.ssc.controlmap.web.dto.PagedResponse;
import io.muzoo.ssc.controlmap.web.dto.SuggestionResponse;
import io.muzoo.ssc.controlmap.web.dto.TransitionRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Findings API (PLAN §10), authenticated. The dashboard list with optional filters (status,
 * severity, framework, free-text q) and pagination, returning the FindingSummary DTO.
 */
@RestController
@RequestMapping("/api")
public class FindingController {

    private final FindingService findingService;
    private final MappingSuggestionService mappingSuggestionService;

    public FindingController(FindingService findingService,
                             MappingSuggestionService mappingSuggestionService) {
        this.findingService = findingService;
        this.mappingSuggestionService = mappingSuggestionService;
    }

    @GetMapping("/findings")
    public PagedResponse<FindingSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String framework,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean deleted,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        return findingService.list(status, severity, framework, q, deleted, mine, principal.getName(), page, size);
    }

    @GetMapping("/findings/{id}")
    public FindingDetail get(@PathVariable Long id) {
        return findingService.get(id);
    }

    @PostMapping("/findings")
    @ResponseStatus(HttpStatus.CREATED)
    public FindingDetail create(@Valid @RequestBody FindingRequest request, Principal principal) {
        return findingService.create(request, principal.getName());
    }

    @PutMapping("/findings/{id}")
    public FindingDetail update(@PathVariable Long id, @Valid @RequestBody FindingRequest request, Principal principal) {
        return findingService.update(id, request, principal.getName());
    }

    @DeleteMapping("/findings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Principal principal) {
        findingService.delete(id, principal.getName());
    }

    @PostMapping("/findings/{id}/controls")
    public FindingDetail addControl(@PathVariable Long id, @Valid @RequestBody AddMappingRequest request, Principal principal) {
        return findingService.addControl(id, request, principal.getName());
    }

    @DeleteMapping("/findings/{id}/controls/{controlId}")
    public FindingDetail removeControl(@PathVariable Long id, @PathVariable Long controlId, Principal principal) {
        return findingService.removeControl(id, controlId, principal.getName());
    }

    /**
     * AI control-mapping suggestions for a finding (stretch, PLAN §10). Owner-only and editable-state,
     * rate-limited and cached; 503 when the feature is off or the model call fails (the UI falls back to
     * manual mapping). Returns recommendations only — accepting one is the existing add-mapping action.
     */
    @PostMapping("/findings/{id}/suggest-controls")
    public List<SuggestionResponse> suggestControls(@PathVariable Long id, Principal principal) {
        return mappingSuggestionService.suggest(id, principal.getName());
    }

    /**
     * Accept an AI-suggested control (stretch, PLAN §3/§4). The client sends only the controlId; the
     * server stamps AI provenance itself from the suggestion it cached (server-authoritative), so the
     * audit trail can't be spoofed. 409 if the control isn't a current suggestion for this finding.
     */
    @PostMapping("/findings/{id}/accept-suggestion")
    public FindingDetail acceptSuggestion(@PathVariable Long id,
                                          @Valid @RequestBody AcceptSuggestionRequest request,
                                          Principal principal) {
        return mappingSuggestionService.accept(id, request.controlId(), principal.getName());
    }

    @PostMapping("/findings/{id}/transition")
    public FindingDetail transition(@PathVariable Long id, @Valid @RequestBody TransitionRequest request, Principal principal) {
        return findingService.transition(id, request, principal.getName());
    }
}
