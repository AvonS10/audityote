package io.muzoo.ssc.controlmap.repository;

import io.muzoo.ssc.controlmap.domain.Control;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Data access for {@link Control}. Lookups by framework back the catalog API (#9) and seeding (#6). */
public interface ControlRepository extends JpaRepository<Control, Long> {

    Optional<Control> findByFramework_IdAndCode(Long frameworkId, String code);

    List<Control> findByFramework_Id(Long frameworkId);

    /** Controls of a framework, in catalog (insertion) order — backs the catalog screen. */
    List<Control> findByFramework_SlugOrderByIdAsc(String slug);

    /**
     * Every control with its {@link io.muzoo.ssc.controlmap.domain.Framework} eagerly fetched, so the
     * returned entities can be read after the transaction closes. Backs the AI suggestion flow (S2),
     * which builds its prompt (and maps controls to DTOs) <em>outside</em> any transaction — the model
     * call must not pin a DB connection — where a lazy {@code framework} access would otherwise fail.
     */
    @Query("select c from Control c join fetch c.framework")
    List<Control> findAllWithFramework();
}
