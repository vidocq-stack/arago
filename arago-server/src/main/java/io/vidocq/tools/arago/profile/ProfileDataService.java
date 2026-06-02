package io.vidocq.tools.arago.profile;

import io.vidocq.tools.arago.persistence.AttendeeProfile;
import io.vidocq.tools.arago.persistence.AttendeeProfileRepository;
import io.vidocq.tools.arago.persistence.ChatMessage;
import io.vidocq.tools.arago.persistence.ChatMessageRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * RGPD data-subject operations on an attendee profile (cf. arago-spec §4.7), resolved by the magic-link
 * token's subject ({@code profileId}). Pure of HTTP — {@code ProfileResource} delegates here.
 *
 * <ul>
 *   <li><b>access / portability</b>: {@link #myData(String)} returns the profile + its persistent
 *       messages (also used verbatim for the JSON export).</li>
 *   <li><b>erasure</b> (right to be forgotten): {@link #erase(String)} anonymises every message authored
 *       by the profile ({@code profileId=null}, {@code authorPseudo="anonyme"} — persistent messages keep
 *       their body but lose all linkage) and deletes the {@link AttendeeProfile}.</li>
 * </ul>
 */
@ApplicationScoped
public class ProfileDataService {

    static final String ANONYMOUS = "anonyme";

    @Inject
    AttendeeProfileRepository profiles;

    @Inject
    ChatMessageRepository messages;

    /** Profile snapshot + its persistent messages, or empty if the profile no longer exists. */
    public Optional<MyData> myData(String profileId) {
        return profiles.findById(profileId).map(p -> new MyData(
                p.getEmail(),
                p.getPseudo(),
                iso(p.getConsentAt()),
                p.getConsentTextVersion(),
                messages.findByProfileId(profileId).stream()
                        .filter(ChatMessage::isPersistent)
                        .map(m -> new MessageView(m.getRoomId(), m.getBody(), iso(m.getAt())))
                        .toList()));
    }

    /** Anonymises all of the profile's messages and deletes the profile. Idempotent. */
    public EraseResult erase(String profileId) {
        List<ChatMessage> authored = messages.findByProfileId(profileId);
        int anonymized = anonymizeInPlace(authored);
        authored.forEach(messages::save);
        boolean deleted = profiles.findById(profileId).isPresent();
        if (deleted) {
            profiles.deleteById(profileId);
        }
        return new EraseResult(deleted, anonymized);
    }

    /**
     * Clears the profile linkage on each message ({@code profileId=null}, {@code authorPseudo="anonyme"})
     * and returns the count. Package-visible and repository-free so the erasure rule is unit-testable.
     */
    static int anonymizeInPlace(List<ChatMessage> authored) {
        for (ChatMessage m : authored) {
            m.setProfileId(null);
            m.setAuthorPseudo(ANONYMOUS);
        }
        return authored.size();
    }

    private static String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    /** "My data" view (access + portability export). */
    public record MyData(String email, String pseudo, String consentAt, String consentTextVersion,
                         List<MessageView> messages) {}

    /** A persistent message in the "my data" view. */
    public record MessageView(String roomId, String body, String at) {}

    /** Outcome of an erasure request. */
    public record EraseResult(boolean profileDeleted, int messagesAnonymized) {}
}
