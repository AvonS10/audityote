package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.web.dto.PostureResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Risk posture API (PLAN §9/§10). {@code GET /api/posture} returns the program-wide rollup behind the
 * Risk Posture screen. Authenticated; both roles may view it (read-only, no separation-of-duties gate).
 */
@RestController
@RequestMapping("/api")
public class PostureController {

    private final PostureService postureService;

    public PostureController(PostureService postureService) {
        this.postureService = postureService;
    }

    @GetMapping("/posture")
    public PostureResponse posture() {
        return postureService.rollup();
    }
}
