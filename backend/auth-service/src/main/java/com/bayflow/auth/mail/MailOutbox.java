package com.bayflow.auth.mail;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.auth.config.AuthProperties;
import com.bayflow.auth.domain.MailMessage;
import com.bayflow.auth.infrastructure.MailOutboxRepository;
import com.bayflow.auth.infrastructure.MailOutboxRepository.DueMail;
import com.bayflow.security.TenantContext;

/** Database side of the e-mail outbox: queueing, finding and settling e-mails. */
@Service
public class MailOutbox {

    /** An e-mail was queued; sent right after the transaction commits (see {@link MailDispatcher}). */
    public record MailQueued(UUID tenantId, UUID messageId) {
    }

    private final MailOutboxRepository outbox;
    private final AuthProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public MailOutbox(MailOutboxRepository outbox, AuthProperties properties, ApplicationEventPublisher events,
            Clock clock) {
        this.outbox = outbox;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    /** Queues the e-mail in the caller's transaction: it is sent if (and only if) that commits. */
    @Transactional
    public void enqueue(OutgoingMail mail) {
        MailMessage message = outbox.save(MailMessage.create(mail.to(), mail.senderName(), mail.replyTo(),
                mail.subject(), mail.text(), mail.html(), clock.instant()));
        events.publishEvent(new MailQueued(TenantContext.require(), message.getId()));
    }

    @Transactional(readOnly = true)
    public List<DueMail> due() {
        return outbox.findDueAcrossTenants(clock.instant(), properties.getMail().getBatchSize());
    }

    @Transactional(readOnly = true)
    public Optional<MailMessage> find(UUID messageId) {
        return outbox.findById(messageId);
    }

    @Transactional
    public void markSent(UUID messageId) {
        MailMessage message = outbox.findById(messageId).orElseThrow();
        if (!message.isProcessed()) {
            message.markSent(clock.instant());
        }
    }

    @Transactional
    public void markFailed(UUID messageId, String error) {
        MailMessage message = outbox.findById(messageId).orElseThrow();
        if (message.isProcessed()) {
            return;
        }
        AuthProperties.Mail settings = properties.getMail();
        message.markFailed(error, clock.instant(), settings.getInitialBackoff(), settings.getMaxBackoff(),
                settings.getGiveUpAfter());
    }
}
