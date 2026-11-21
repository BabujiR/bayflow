package com.bayflow.auth.mail;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.bayflow.auth.config.AuthProperties;
import com.bayflow.auth.domain.MailMessage;
import com.bayflow.auth.infrastructure.MailOutboxRepository.DueMail;
import com.bayflow.auth.mail.MailOutbox.MailQueued;
import com.bayflow.security.TenantContext;

/**
 * Sends queued e-mails: right after the change commits (in the background, so a slow mail server
 * never slows the request) and then on a schedule until they go out or are given up.
 */
@Component
public class MailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);

    private final MailOutbox outbox;
    private final MailGateway gateway;
    private final AuthProperties properties;
    private final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();

    public MailDispatcher(MailOutbox outbox, MailGateway gateway, AuthProperties properties) {
        this.outbox = outbox;
        this.gateway = gateway;
        this.properties = properties;
    }

    @TransactionalEventListener
    public void onQueued(MailQueued queued) {
        if (!properties.getMail().isSendImmediately()) {
            return;
        }
        background.execute(() -> TenantContext.run(queued.tenantId(), () -> {
            try {
                outbox.find(queued.messageId()).filter(m -> !m.isProcessed()).ifPresent(this::deliver);
            } catch (RuntimeException e) {
                log.warn("Could not send e-mail {}: {}", queued.messageId(), e.getMessage());
            }
        }));
    }

    /** Sends every e-mail whose next attempt is due, in all workshops. Returns how many were tried. */
    public int dispatchDue() {
        List<DueMail> due = outbox.due();
        due.forEach(next -> TenantContext.run(next.getTenantId(), () -> outbox.find(next.getId())
                .filter(message -> !message.isProcessed())
                .ifPresent(this::deliver)));
        return due.size();
    }

    @PreDestroy
    void stop() {
        background.shutdown();
    }

    private void deliver(MailMessage message) {
        try {
            gateway.send(new OutgoingMail(message.getRecipient(), message.getSenderName(), message.getReplyTo(),
                    message.getSubject(), message.getTextBody(), message.getHtmlBody()));
        } catch (RuntimeException e) {
            // Never log the recipient or the body: the body holds a reset link.
            log.warn("Could not send e-mail {} (attempt {}): {}", message.getId(), message.getAttempts() + 1,
                    e.getMessage());
            settle(() -> outbox.markFailed(message.getId(), e.getClass().getSimpleName() + ": " + e.getMessage()),
                    message);
            return;
        }
        settle(() -> outbox.markSent(message.getId()), message);
    }

    private static void settle(Runnable update, MailMessage message) {
        try {
            update.run();
        } catch (ObjectOptimisticLockingFailureException e) {
            log.debug("E-mail {} was settled concurrently", message.getId());
        }
    }
}
