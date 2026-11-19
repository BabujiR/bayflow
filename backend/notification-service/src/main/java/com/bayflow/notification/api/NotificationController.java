package com.bayflow.notification.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.notification.application.ApiException;
import com.bayflow.notification.application.NotificationService;
import com.bayflow.notification.config.RecipientContext;
import com.bayflow.notification.domain.EventType;
import com.bayflow.notification.domain.Notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** The signed-in user's own notifications. Nobody can see or mark another user's. */
@RestController
@RequestMapping("/api/v1/notifications")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('CUSTOMER', 'SERVICE_ADVISOR', 'MECHANIC', 'MANAGER', 'ADMIN')")
@Tag(name = "Notifications")
public class NotificationController {

    public record NotificationResponse(UUID id, EventType type, String title, String body, String link,
            UUID locationId, Instant createdAt, boolean read) {

        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(),
                    n.getLocationId(), n.getCreatedAt(), n.isRead());
        }
    }

    public record PageResponse(List<NotificationResponse> content, int page, int size, long totalElements,
            int totalPages) {

        static PageResponse from(Page<Notification> page) {
            return new PageResponse(page.getContent().stream().map(NotificationResponse::from).toList(),
                    page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }

    public record CountResponse(long count) {
    }

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    @Operation(summary = "Your notifications, newest first")
    public PageResponse list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(notifications.mine(me(), Paging.page(page), Paging.size(size)));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "How many of your notifications are unread")
    public CountResponse unreadCount() {
        return new CountResponse(notifications.unreadCount(me()));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark one of your notifications read (404 for anyone else's)")
    public NotificationResponse markRead(@PathVariable UUID id) {
        return NotificationResponse.from(notifications.markRead(me(), id));
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all your notifications read; returns how many changed")
    public CountResponse markAllRead() {
        return new CountResponse(notifications.markAllRead(me()));
    }

    private static UUID me() {
        return RecipientContext.currentUser()
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "forbidden",
                        "Forbidden", "Only signed-in users have notifications."));
    }
}
