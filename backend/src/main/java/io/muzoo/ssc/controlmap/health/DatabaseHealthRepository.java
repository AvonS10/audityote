package io.muzoo.ssc.controlmap.health;

/**
 * Data-access abstraction for a database liveness probe.
 *
 * <p>This is the <b>Repository (stub)</b> for chunk #3: a thin port over the database that the
 * controller depends on through this interface, not on JDBC/JPA details (Dependency Inversion).
 * Real Spring Data JPA repositories over the domain entities arrive in chunk #5; they will follow
 * the same principle — callers depend on repository interfaces, never on the persistence mechanism.
 */
public interface DatabaseHealthRepository {

    /**
     * @return {@code true} if a trivial round-trip query to the database succeeds.
     */
    boolean isReachable();
}
