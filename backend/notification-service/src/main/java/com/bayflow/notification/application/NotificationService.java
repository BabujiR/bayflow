package com.bayflow.notification.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.notification.application.NotificationTexts.Text;
import com.bayflow.notification.application.Recipients.Audience;
import com.bayflow.notification.config.NotificationProperties;
import com.bayflow.notification.config.RecipientContext;
import com.bayflow.notification.domain.Notification;
import com.bayflow.notification.domain.ReceivedEvent;
import com.bayflow.notification.infrastructure.NotificationRepository;
import com.bayflow.notification.integration.AuthClient;
import com.bayflow.notification.integration.AuthClient.LocationInfo;
import com.bayflow.security.TenantContext;

/** Stores each event's notifications, and lets every user read and mark their own. */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** What storing an event did. */
    public record Received(boolean duplicate, int notified) {
    }

    private final NotificationRepository notifications;
    private final EventInbox inbox;
    private final Recipients recipients;
    private final AuthClient auth;
    private final NotificationProperties properties;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, EventInbox inbox,
            Recipients recipients, AuthClient auth, NotificationProperties properties, Clock clock) {
        this.notifications = notifications;
        this.inbox = inbox;
        this.recipients = recipients;
        this.auth = auth;
        this.properties = properties;
        this.clock = clock;
    }

    // ---- events (SYSTEM) -------------------------------------------------------------------

    /**
     * Stores the event's notifications, once: a repeated delivery is acknowledged and ignored.
     * Who is told is decided now (later changes to a user's locations don't move old ones).
     * Throws 503 when auth-service can't say who works at the location, so the sender retries.
     */
    public Received receive(NotificationEvent event) {
        return TenantContext.call(event.tenantId(), () -> RecipientContext.asService(() -> {
            if (inbox.alreadyReceived(event.eventId())) {
                return new Received(true, 0);
            }
            Map<UUID, Audience> who;
            try {
                who = recipients.of(event);
            } catch (RuntimeException e) {
                log.warn("Could not find who to notify about {} {} ({})", event.type(), event.eventId(),
                        e.getMessage());
                throw ApiException.unavailable("recipients-unavailable",
                        "Could not find who to notify. Try again shortly.");
            }
            ZoneId zone = zoneOf(event.tenantId(), event.locationId());
            Instant now = clock.instant();
            List<Notification> toStore = new ArrayList<>();
            who.forEach((user, audience) -> {
                Text text = NotificationTexts.of(event, audience, zone);
                toStore.add(Notification.create(event.eventId(), event.type(), event.locationId(), user,
                        text.title(), text.body(), text.link(), now));
            });
            return inbox.store(ReceivedEvent.of(event.eventId(), event.type(), now), toStore);
        }));
    }

    // ---- a user's own notifications --------------------------------------------------------

    /** The user's notifications (all unread: reading deletes them), newest first. */
    @Transactional(readOnly = true)
    public Page<Notification> mine(UUID user, int page, int size) {
        return notifications.findForRecipient(user, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID user) {
        return notifications.countByRecipientId(user);
    }

    /** Reading a notification deletes it; another user's (or one already read) is "not found". */
    @Transactional
    public void markRead(UUID user, UUID id) {
        if (notifications.deleteOwn(id, user) == 0) {
            throw ApiException.notFound("Notification");
        }
    }

    /** Deletes all the user's notifications; returns how many there were. */
    @Transactional
    public int markAllRead(UUID user) {
        return notifications.deleteAllOwn(user);
    }

    // ---- clean-up -----------------------------------------------------------------------------

    /** Deletes notifications older than the configured retention, in every workshop. */
    @Transactional
    public int deleteOld() {
        Instant cutoff = clock.instant().minus(properties.getRetention().getKeep());
        int removed = notifications.deleteBefore(cutoff);
        if (removed > 0) {
            log.info("Deleted {} notifications older than {}", removed, cutoff);
        }
        return removed;
    }

    ZoneId zoneOf(UUID tenantId, UUID locationId) {
        if (locationId == null) {
            return properties.getFallbackZone();
        }
        try {
            return auth.locations(tenantId).stream()
                    .filter(l -> l.id().equals(locationId))
                    .map(LocationInfo::timeZone)
                    .findFirst()
                    .map(ZoneId::of)
                    .orElse(properties.getFallbackZone());
        } catch (RuntimeException e) {
            log.warn("No time zone for location {} ({}); using {}", locationId, e.getMessage(),
                    properties.getFallbackZone());
            return properties.getFallbackZone();
        }
    }
}
