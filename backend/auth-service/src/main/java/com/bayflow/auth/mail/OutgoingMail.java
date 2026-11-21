package com.bayflow.auth.mail;

/** One e-mail as handed to the mail server. {@code replyTo} may be null. */
public record OutgoingMail(String to, String senderName, String replyTo, String subject, String text, String html) {
}
