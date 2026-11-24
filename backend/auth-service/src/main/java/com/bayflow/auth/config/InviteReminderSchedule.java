package com.bayflow.auth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.bayflow.auth.application.InvitationService;
import com.bayflow.auth.domain.Platform;
import com.bayflow.security.TenantContext;

/** Hourly: reminds owners of setup links about to expire unused. Off in tests (they call it). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "bayflow.auth.signup", name = "invite-reminders-enabled", havingValue = "true",
        matchIfMissing = true)
public class InviteReminderSchedule {

    private final InvitationService invitations;

    public InviteReminderSchedule(InvitationService invitations) {
        this.invitations = invitations;
    }

    @Scheduled(cron = "${bayflow.auth.signup.invite-reminder-cron:0 7 * * * *}")
    void remind() {
        // Invites are platform-level; run as the console's organization like the console does.
        TenantContext.run(Platform.TENANT_ID, invitations::sendReminders);
    }
}
