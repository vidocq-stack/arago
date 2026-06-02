package io.vidocq.tools.arago.ws;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import io.vidocq.tools.arago.persistence.HelpRequest;
import io.vidocq.tools.arago.persistence.HelpStatus;

import org.junit.jupiter.api.Test;

class HelpCooldownTest {

    private static final Instant NOW = Instant.parse("2026-06-02T12:00:00Z");

    private static HelpRequest help(HelpStatus status, Instant updatedAt) {
        HelpRequest h = new HelpRequest("h", "room", "grace", null, null, status, NOW.minusSeconds(300));
        h.setStatus(status);
        h.setUpdatedAt(updatedAt);
        return h;
    }

    @Test
    void blocksWhenResolvedWithinCooldown() {
        var helps = List.of(help(HelpStatus.RESOLVED, NOW.minusSeconds(10)));
        assertTrue(RoomSocket.inResolveCooldown(helps, NOW, 60));
    }

    @Test
    void allowsWhenResolvedBeforeCooldown() {
        var helps = List.of(help(HelpStatus.RESOLVED, NOW.minusSeconds(120)));
        assertFalse(RoomSocket.inResolveCooldown(helps, NOW, 60));
    }

    @Test
    void onlyResolvedCounts() {
        // A recently CANCELLED request is a voluntary withdrawal — no cooldown.
        var helps = List.of(help(HelpStatus.CANCELLED, NOW.minusSeconds(5)));
        assertFalse(RoomSocket.inResolveCooldown(helps, NOW, 60));
    }

    @Test
    void zeroCooldownNeverBlocks() {
        var helps = List.of(help(HelpStatus.RESOLVED, NOW.minusSeconds(1)));
        assertFalse(RoomSocket.inResolveCooldown(helps, NOW, 0));
    }

    @Test
    void emptyHistoryAllows() {
        assertFalse(RoomSocket.inResolveCooldown(List.of(), NOW, 60));
    }
}
