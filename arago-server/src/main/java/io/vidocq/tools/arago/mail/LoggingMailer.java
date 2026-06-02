package io.vidocq.tools.arago.mail;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Default {@link Mailer}: logs that an email would be sent, <strong>without</strong> the link (a bearer
 * credential) at INFO. Arago has no SMTP wiring yet (cf. arago-spec §4.7); this keeps the RGPD flow
 * functional in every environment. A real {@code SmtpMailer} replaces this behind the {@link Mailer} SPI.
 *
 * <p>For local/dev testing, {@code POST /api/profile/magic-link} can additionally return the link in its
 * response when {@code arago.mail.dev-expose-link=true} — that affordance lives in the resource, not here.</p>
 */
@ApplicationScoped
public class LoggingMailer implements Mailer {

    private static final System.Logger LOG = System.getLogger(LoggingMailer.class.getName());

    @Override
    public void sendMagicLink(String email, String link) {
        // Never log the link/token at INFO — log only that a magic link was requested for this address.
        LOG.log(System.Logger.Level.INFO, "Magic link generated for {0} (email delivery is not configured)", email);
        LOG.log(System.Logger.Level.DEBUG, () -> "Magic link for " + email + ": " + link);
    }

    @Override
    public void sendValidationLink(String email, String link) {
        LOG.log(System.Logger.Level.INFO,
                "Email validation link generated for {0} (email delivery is not configured)", email);
        LOG.log(System.Logger.Level.DEBUG, () -> "Validation link for " + email + ": " + link);
    }
}
