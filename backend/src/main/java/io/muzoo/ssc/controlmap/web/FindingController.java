package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.web.dto.FindingSummary;
import io.muzoo.ssc.controlmap.web.dto.PagedResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
}
