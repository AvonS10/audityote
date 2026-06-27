package io.muzoo.ssc.controlmap.repository;

import io.muzoo.ssc.controlmap.domain.FindingControlMapping;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for the {@link FindingControlMapping} join — backs the mapping panel API (#12). */
public interface FindingControlMappingRepository extends JpaRepository<FindingControlMapping, Long> {

    List<FindingControlMapping> findByFinding_Id(Long findingId);

    Optional<FindingControlMapping> findByFinding_IdAndControl_Id(Long findingId, Long controlId);

    boolean existsByFinding_IdAndControl_Id(Long findingId, Long controlId);

    /** Batch-fetch mappings (with control + framework) for several findings — avoids an N+1 in lists. */
    @Query("""
            select m from FindingControlMapping m
            join fetch m.control c join fetch c.framework
            where m.finding.id in :findingIds
            """)
    List<FindingControlMapping> findWithControlByFindingIds(@Param("findingIds") Collection<Long> findingIds);

    /** All mappings (with their finding) for the controls of one framework — backs coverage rollup (#13). */
    @Query("""
            select m from FindingControlMapping m
            join fetch m.finding
            join fetch m.control c
            where c.framework.slug = :slug
            """)
    List<FindingControlMapping> findWithFindingByFrameworkSlug(@Param("slug") String slug);
}
