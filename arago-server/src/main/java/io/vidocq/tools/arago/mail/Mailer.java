package io.vidocq.tools.arago.mail;

/**
 * Outbound email abstraction (cf. arago-spec §4.7). Arago itself dispatches the few emails it sends —
 * the RGPD magic link, and (later) speaker replies to persistent questions — so an attendee only ever
 * gives their address to Arago, never to a speaker.
 *
 * <p>The default implementation ({@link LoggingMailer}) only logs: Arago has no SMTP wiring yet. A real
 * {@code SmtpMailer} is a later, drop-in implementation behind this SPI; nothing else changes.</p>
 */
public interface Mailer {

    /**
     * Sends (or, for the logging default, records) a magic link to the attendee's address. Must never
     * leak the link/token at INFO level (it is a bearer credential).
     */
    void sendMagicLink(String email, String link);

    /**
     * Sends the email-validation magic link triggered by an attendee's first persistent message
     * (§4.7/§10.1). Same credential-handling rule: never log the link at INFO.
     */
    void sendValidationLink(String email, String link);
}
