package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.audit.FindingAuditEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Observer that drops a finding's cached AI suggestions whenever the finding changes (Observer pattern,
 * PLAN §11) — the same {@link FindingAuditEvent} the {@link io.muzoo.ssc.controlmap.audit.AuditTrailListener}
 * records is also the "content changed" signal for the {@link SuggestionCache}. Without this, an analyst
 * who edits a finding and re-clicks "Suggest controls" within the cache TTL would get suggestions (and, on
 * accept, an audit-trail rationale) describing the <em>pre-edit</em> content.
 *
 * <p>Riding the existing event keeps {@link FindingService} AI-agnostic (it never learns about the cache)
 * and covers every mutation path at once — edit, delete, map/unmap, accept, transition — including any added
 * later (OCP). A no-op edit publishes no event, so the still-valid cache is correctly left intact.
 */
@Component
public class SuggestionCacheInvalidator {

    private final SuggestionCache cache;

    public SuggestionCacheInvalidator(SuggestionCache cache) {
        this.cache = cache;
    }

    @EventListener
    public void on(FindingAuditEvent event) {
        Long findingId = event.finding().getId();
        if (findingId != null) {
            cache.invalidate(findingId);
        }
    }
}
