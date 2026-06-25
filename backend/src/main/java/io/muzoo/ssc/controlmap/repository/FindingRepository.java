package io.muzoo.ssc.controlmap.repository;

import io.muzoo.ssc.controlmap.domain.Finding;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for {@link Finding}. {@code findByReference} resolves the human-facing CM-YYYY-NNNN id;
 * {@code existsByReference} guards reference generation against collisions.
 */
public interface FindingRepository extends JpaRepository<Finding, Long> {

    Optional<Finding> findByReference(String reference);

    boolean existsByReference(String reference);
}
