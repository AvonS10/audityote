package io.muzoo.ssc.controlmap.health;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness/readiness endpoint for the API. Reports app status plus a real database round-trip,
 * so it doubles as the chunk #3 smoke test that the Postgres connection is wired correctly.
 *
 * <p>Returns {@code 200} when the database is reachable and {@code 503} when it is not, with a
 * small JSON body and no internal details (no stack traces, no driver messages).
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final DatabaseHealthRepository databaseHealth;

    public HealthController(DatabaseHealthRepository databaseHealth) {
        this.databaseHealth = databaseHealth;
    }

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        boolean dbUp = databaseHealth.isReachable();
        HealthResponse body = new HealthResponse(
                dbUp ? "up" : "down",
                dbUp ? "up" : "down",
                Instant.now());
        return ResponseEntity
                .status(dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
    }

    /** Immutable health payload: overall app status, database status, and a server timestamp. */
    public record HealthResponse(String status, String db, Instant timestamp) {
    }
}
