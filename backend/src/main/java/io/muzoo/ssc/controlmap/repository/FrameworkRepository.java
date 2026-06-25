package io.muzoo.ssc.controlmap.repository;

import io.muzoo.ssc.controlmap.domain.Framework;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link Framework}. Lookup by slug backs the catalog API and idempotent seeding. */
public interface FrameworkRepository extends JpaRepository<Framework, Long> {

    Optional<Framework> findBySlug(String slug);

    List<Framework> findAllByOrderByNameAsc();
}
