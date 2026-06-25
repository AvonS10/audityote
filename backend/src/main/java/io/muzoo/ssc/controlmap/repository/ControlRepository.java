package io.muzoo.ssc.controlmap.repository;

import io.muzoo.ssc.controlmap.domain.Control;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link Control}. Lookups by framework back the catalog API (#9) and seeding (#6). */
public interface ControlRepository extends JpaRepository<Control, Long> {

    Optional<Control> findByFramework_IdAndCode(Long frameworkId, String code);

    List<Control> findByFramework_Id(Long frameworkId);
}
