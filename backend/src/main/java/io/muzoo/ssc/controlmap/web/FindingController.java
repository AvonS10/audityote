package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.web.dto.FindingDetail;
import io.muzoo.ssc.controlmap.web.dto.FindingRequest;
import io.muzoo.ssc.controlmap.web.dto.FindingSummary;
import io.muzoo.ssc.controlmap.web.dto.PagedResponse;
import jakarta.validation.Valid;
import java.security.Principal;
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

    public FindingController(FindingService findingService) {
        this.findingService = findingService;
    }

    @GetMapping("/findings")
    public PagedResponse<FindingSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String framework,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return findingService.list(status, severity, framework, q, page, size);
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
}
