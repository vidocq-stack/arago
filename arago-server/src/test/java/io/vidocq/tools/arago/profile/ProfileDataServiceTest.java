package io.vidocq.tools.arago.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;

import io.vidocq.tools.arago.persistence.ChatMessage;

import org.junit.jupiter.api.Test;

class ProfileDataServiceTest {

    private static ChatMessage msg(String id, String profileId, String pseudo, boolean persistent) {
        return new ChatMessage(id, "room-1", profileId, pseudo, false, persistent, "hello", Instant.now(), null);
    }

    @Test
    void anonymizeClearsLinkageAndCounts() {
        List<ChatMessage> authored = List.of(
                msg("m1", "p-42", "Ada", true),
                msg("m2", "p-42", "Ada", false));

        int count = ProfileDataService.anonymizeInPlace(authored);

        assertEquals(2, count);
        for (ChatMessage m : authored) {
            assertNull(m.getProfileId(), "profileId must be cleared on erasure");
            assertEquals("anonyme", m.getAuthorPseudo(), "pseudo must be anonymised");
            assertEquals("hello", m.getBody(), "the message body itself is preserved");
        }
    }

    @Test
    void anonymizeOfNothingIsZero() {
        assertEquals(0, ProfileDataService.anonymizeInPlace(List.of()));
    }
}
