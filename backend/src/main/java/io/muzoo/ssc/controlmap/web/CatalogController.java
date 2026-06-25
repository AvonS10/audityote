package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.web.dto.ControlResponse;
import io.muzoo.ssc.controlmap.web.dto.FrameworkResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only catalog API (PLAN §10), authenticated (any signed-in user). Frameworks and the controls
 * within a framework, with optional text search — backs the Control Catalog screen.
 */
@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/frameworks")
    public List<FrameworkResponse> frameworks() {
        return catalogService.listFrameworks();
    }

    @GetMapping("/controls")
    public List<ControlResponse> controls(
            @RequestParam("framework") String framework,
            @RequestParam(value = "q", required = false) String q) {
        return catalogService.listControls(framework, q);
    }
}
