package com.bayflow.notification.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.notification.application.NotificationService.Received;
import com.bayflow.notification.domain.Notification;
import com.bayflow.notification.domain.ReceivedEvent;
import com.bayflow.notification.infrastructure.NotificationRepository;
import com.bayflow.notification.infrastructure.ReceivedEventRepository;

/**
 * The database side of receiving an event: the event's id and its notifications are stored in
 * one transaction, so an event is either fully stored or not at all (and then retried).
 */
@Component
public class EventInbox {

    private final ReceivedEventRepository received;
    private final NotificationRepository notifications;

    public EventInbox(ReceivedEventRepository received, NotificationRepository notifications) {
        this.received = received;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public boolean alreadyReceived(UUID eventId) {
        return received.existsById(eventId);
    }

    /**
     * Stores the event and its notifications. If the same event was stored meanwhile (two
     * deliveries at once), the unique keys refuse the second; the caller gets a 409 and the
     * sender's retry then finds it received.
     */
    @Transactional
    public Received store(ReceivedEvent event, List<Notification> toStore) {
        if (received.existsById(event.getEventId())) {
            return new Received(true, 0);
        }
        received.saveAndFlush(event);
        notifications.saveAllAndFlush(toStore);
        return new Received(false, toStore.size());
    }
}
