package com.bayflow.notification.api;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.notification.application.NotificationEvent;
import com.bayflow.notification.application.NotificationService;
import com.bayflow.notification.application.NotificationService.Received;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Where appointment-service and workshop-service deliver events from their outboxes. SYSTEM
 * tokens only; nginx does not route /api/v1/internal/ from outside.
 */
@RestController
@RequestMapping("/api/v1/internal/notification-events")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Internal")
public class InternalEventController {

    private final NotificationService notifications;

    public InternalEventController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @PostMapping
    @PreAuthorize("hasRole('SYSTEM')")
    @Operation(summary = "Deliver an event (SYSTEM). Idempotent on eventId; 503 = try again later")
    public Received receive(@Valid @RequestBody NotificationEvent event) {
        return notifications.receive(event);
    }
}
