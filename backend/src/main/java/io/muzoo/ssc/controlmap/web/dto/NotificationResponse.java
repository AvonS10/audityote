package io.muzoo.ssc.controlmap.web.dto;

import java.time.Instant;

/**
 * One return-notification (#4): a finding the current user owns that a reviewer handed back
 * (status RETURNED), carrying the reviewer's comment so the analyst sees why and can jump to it.
 */
public record NotificationResponse(
        Long findingId,
        String reference,
        String title,
        String severity,
        String returnedBy,
        Instant returnedAt,
        String comment) {
}
