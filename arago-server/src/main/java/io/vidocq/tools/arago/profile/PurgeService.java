package io.vidocq.tools.arago.profile;

import io.vidocq.tools.arago.attachments.AttachmentStore;
import io.vidocq.tools.arago.persistence.ChatMessage;
import io.vidocq.tools.arago.persistence.ChatMessageRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;

/**
 * Applies the RGPD retention policy (cf. arago-spec §4.7). Phase 1 covers the one rule with a ready
 * query: ephemeral chat messages ({@code persistent=false}) past their {@code purgeAfter} instant are
 * deleted. Idempotent — re-running purges only what is currently due.
 *
 * <p>Triggered manually via {@code POST /api/admin/purge/run} (superadmin). A scheduled daily run is
 * deferred (the stack has no scheduler primitive yet); help-request / non-SECRET-pin retention by
 * ended-room age is a later extension.</p>
 */
@ApplicationScoped
public class PurgeService {

    @Inject
    ChatMessageRepository messages;

    @Inject
    AttachmentStore attachments;

    /** Deletes due ephemeral messages (and expired attachments) and returns the counts. */
    public PurgeResult run() {
        Instant now = Instant.now();
        List<ChatMessage> due = messages.findByPersistentFalseAndPurgeAfterLessThan(now);
        for (ChatMessage m : due) {
            messages.deleteById(m.getId());
        }
        attachments.deleteExpired(now); // RGPD: drop blobs past their retention (§4.7)
        return new PurgeResult(due.size());
    }

    /** Counts of what a purge run removed. */
    public record PurgeResult(int ephemeralChatPurged) {}
}
