package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.web.dto.FindingSummary;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reviewer sign-off queue (PLAN §10/§14 #17). The queue lists the SUBMITTED findings awaiting a
 * decision; the approve / return-for-changes decisions themselves go through the transition endpoint
 * ({@code POST /api/findings/{id}/transition}), which enforces the full State machine and separation
 * of duties (§8).
 *
 * <p>Access is Reviewer-only and the boundary is server-side: declarative method security
 * ({@code @PreAuthorize}) is the separation-of-duties gate here, not a hidden UI button. A wrong-role
 * request is rejected 403; an unauthenticated one 401 (by the security filter chain).
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final FindingService findingService;

    public ReviewController(FindingService findingService) {
        this.findingService = findingService;
    }

    @GetMapping("/queue")
    @PreAuthorize("hasRole('REVIEWER')")
    public List<FindingSummary> queue() {
        return findingService.reviewQueue();
    }
}
