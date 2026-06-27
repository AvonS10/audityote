package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.web.dto.CoverageRow;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only coverage API (PLAN §10), authenticated (any signed-in user). Per-control finding counts,
 * worst severity and at-risk flags for one framework — backs the Control Coverage screen (#13).
 */
@RestController
@RequestMapping("/api")
public class CoverageController {

    private final CoverageService coverageService;

    public CoverageController(CoverageService coverageService) {
        this.coverageService = coverageService;
    }

    @GetMapping("/coverage")
    public List<CoverageRow> coverage(@RequestParam("framework") String framework) {
        return coverageService.coverage(framework);
    }
}
