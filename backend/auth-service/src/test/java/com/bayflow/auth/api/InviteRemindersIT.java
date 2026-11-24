package com.bayflow.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.bayflow.auth.IntegrationTestBase;
import com.bayflow.auth.application.InvitationService;
import com.bayflow.auth.domain.Platform;
import com.bayflow.auth.mail.MailDispatcher;
import com.bayflow.security.TenantContext;

/** One reminder when an unused setup link is about to expire (the hourly job, run by hand here). */
@Import(PasswordResetByEmailIT.CapturedMail.class)
@TestPropertySource(properties = "bayflow.auth.demo.notify-email=sales@example.test")
class InviteRemindersIT extends IntegrationTestBase {

    @Autowired
    private PasswordResetByEmailIT.CapturingGateway mail;

    @Autowired
    private MailDispatcher dispatcher;

    @Autowired
    private InvitationService invitations;

    private final JdbcTemplate jdbc = ownerJdbc();

    @Test
    void anUnusedLinkAboutToExpireIsRemindedOnce() throws Exception {
        mail.available = true;
        String soon = "soon-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String later = "later-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        TenantContext.run(Platform.TENANT_ID, () -> {
            invitations.create("team", soon, "Ana Silva", "Lotus Auto", null);
            invitations.create("team", later, null, "Far Away Garage", null);
        });
        jdbc.update("update bayflow.auth_invitations set expires_at = now() + interval '1 day' where email = ?", soon);
        dispatcher.dispatchDue();
        int before = mail.to(soon).size();

        TenantContext.run(Platform.TENANT_ID, invitations::sendReminders);
        dispatcher.dispatchDue();
        assertThat(mail.to(soon)).hasSize(before + 1).last().satisfies(sent -> {
            assertThat(sent.subject()).startsWith("Your Ramplight setup link expires");
            assertThat(sent.text()).contains("Hello Ana Silva").contains("Set up Lotus Auto on Ramplight");
            assertThat(sent.replyTo()).isEqualTo("sales@example.test");
        });
        // Still 7 days to go: no reminder.
        assertThat(mail.to(later)).noneSatisfy(sent -> assertThat(sent.subject()).startsWith("Your Ramplight"));

        // Only once.
        TenantContext.run(Platform.TENANT_ID, invitations::sendReminders);
        dispatcher.dispatchDue();
        assertThat(mail.to(soon)).hasSize(before + 1);
        assertThat(jdbc.queryForObject("select reminded_at is not null from bayflow.auth_invitations where email = ?",
                Boolean.class, soon)).isTrue();
    }
}
