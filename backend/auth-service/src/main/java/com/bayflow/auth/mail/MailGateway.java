package com.bayflow.auth.mail;

/** Hands e-mails to a mail server. */
public interface MailGateway {

    /** Whether e-mail is set up at all (otherwise "Forgot password?" is not offered). */
    boolean isAvailable();

    /** Sends now; throws when the server refuses or can't be reached (the outbox retries). */
    void send(OutgoingMail mail);
}
