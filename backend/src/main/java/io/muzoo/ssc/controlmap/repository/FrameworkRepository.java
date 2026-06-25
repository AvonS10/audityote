package io.muzoo.ssc.controlmap.repository;

import io.muzoo.ssc.controlmap.domain.Framework;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link Framework}. {@code findByNameAndVersion} backs idempotent seeding (#6). */
public interface FrameworkRepository extends JpaRepository<Framework, Long> {

    Optional<Framework> findByNameAndVersion(String name, String version);
}
