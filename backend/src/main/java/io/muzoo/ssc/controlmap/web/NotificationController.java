package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.web.dto.NotificationResponse;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Return-notifications API (#4): findings handed back to the signed-in user, with the reviewer's
 * comment. Authenticated; scoped to the caller (each user only ever sees their own).
 */
@RestController
@RequestMapping("/api")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/notifications")
    public List<NotificationResponse> notifications(Principal principal) {
        return notificationService.forUser(principal.getName());
    }
}
