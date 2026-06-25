package io.muzoo.ssc.controlmap.repository;

import io.muzoo.ssc.controlmap.domain.FindingControlMapping;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for the {@link FindingControlMapping} join — backs the mapping panel API (#12). */
public interface FindingControlMappingRepository extends JpaRepository<FindingControlMapping, Long> {

    List<FindingControlMapping> findByFinding_Id(Long findingId);

    Optional<FindingControlMapping> findByFinding_IdAndControl_Id(Long findingId, Long controlId);

    boolean existsByFinding_IdAndControl_Id(Long findingId, Long controlId);
}
