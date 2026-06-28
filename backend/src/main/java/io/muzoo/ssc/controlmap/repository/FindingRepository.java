package io.muzoo.ssc.controlmap.repository;

import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.domain.FindingStatus;
import io.muzoo.ssc.controlmap.domain.Severity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for {@link Finding}. {@code findByReference} resolves the human-facing CM-YYYY-NNNN id;
 * {@code search} backs the dashboard list with optional filters and pagination.
 */
public interface FindingRepository extends JpaRepository<Finding, Long> {

    Optional<Finding> findByReference(String reference);

    boolean existsByReference(String reference);

    /**
     * Highest existing reference for a year prefix (e.g. "CM-2026-") — drives reference generation.
     * Intentionally includes soft-deleted findings so a deleted finding's reference is never reused.
     */
    Optional<Finding> findFirstByReferenceStartingWithOrderByReferenceDesc(String prefix);

    /** Active (not soft-deleted) findings, newest first — backs report export (#14/#16c). */
    List<Finding> findByDeletedAtIsNull(Sort sort);

    /**
     * Active findings in a given status, with the owner fetch-joined (no N+1) — backs the Reviewer
     * sign-off queue (#17): pass {@code SUBMITTED} for findings awaiting a decision.
     */
    @Query("select f from Finding f join fetch f.owner where f.status = :status and f.deletedAt is null")
    List<Finding> findByStatusWithOwner(@Param("status") FindingStatus status, Sort sort);

    /**
     * One lightweight row per active (not soft-deleted) finding — severity, status, last-updated —
     * for the posture rollup (#19). Single query, no entity graph.
     */
    @Query("select new io.muzoo.ssc.controlmap.web.PostureRow(f.severity, f.status, f.updatedAt) "
            + "from Finding f where f.deletedAt is null")
    List<io.muzoo.ssc.controlmap.web.PostureRow> findPostureRows();

    /**
     * Paged findings with optional filters (null = ignore). {@code deleted=false} returns active
     * findings; {@code deleted=true} returns the soft-deleted ones (the "Show deleted" view). The
     * owner is fetch-joined to avoid an N+1; the framework filter matches findings having a mapped
     * control in that framework.
     */
    @Query(value = """
            select f from Finding f join fetch f.owner
            where ((:deleted = false and f.deletedAt is null) or (:deleted = true and f.deletedAt is not null))
              and (:status is null or f.status = :status)
              and (:severity is null or f.severity = :severity)
              and (:ownerEmail is null or f.owner.email = :ownerEmail)
              and (:q = '' or lower(f.reference) like concat('%', :q, '%')
                            or lower(f.title) like concat('%', :q, '%'))
              and (:framework is null or exists (
                  select 1 from FindingControlMapping m
                  where m.finding = f and m.control.framework.slug = :framework))
            """,
            countQuery = """
            select count(f) from Finding f
            where ((:deleted = false and f.deletedAt is null) or (:deleted = true and f.deletedAt is not null))
              and (:status is null or f.status = :status)
              and (:severity is null or f.severity = :severity)
              and (:ownerEmail is null or f.owner.email = :ownerEmail)
              and (:q = '' or lower(f.reference) like concat('%', :q, '%')
                            or lower(f.title) like concat('%', :q, '%'))
              and (:framework is null or exists (
                  select 1 from FindingControlMapping m
                  where m.finding = f and m.control.framework.slug = :framework))
            """)
    Page<Finding> search(@Param("status") FindingStatus status,
                         @Param("severity") Severity severity,
                         @Param("ownerEmail") String ownerEmail,
                         @Param("q") String q,
                         @Param("framework") String framework,
                         @Param("deleted") boolean deleted,
                         Pageable pageable);
}
