package io.vidocq.tools.arago.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class OidcFlowStoreTest {

    /** A clock the test advances by hand to exercise TTL expiry deterministically. */
    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final OidcFlowStore store = new OidcFlowStore(now::get);

    @Test
    void loginIsConsumedExactlyOnce() {
        store.putLogin("state-1", "verifier-1", "nonce-1");

        var first = store.consumeLogin("state-1");
        assertTrue(first.isPresent());
        assertEquals("verifier-1", first.get().codeVerifier());
        assertEquals("nonce-1", first.get().nonce());

        // Single-use: a replay of the same state yields nothing (CSRF / code-injection guard).
        assertTrue(store.consumeLogin("state-1").isEmpty());
    }

    @Test
    void expiredLoginIsRejected() {
        store.putLogin("state-2", "v", "n");
        now.addAndGet(OidcFlowStore.LOGIN_TTL_MS + 1);
        assertTrue(store.consumeLogin("state-2").isEmpty());
    }

    @Test
    void unknownStateYieldsEmpty() {
        assertTrue(store.consumeLogin("never-issued").isEmpty());
        assertTrue(store.consumeLogin(null).isEmpty());
    }

    @Test
    void ticketIsConsumedExactlyOnce() {
        String ticket = store.putTicket("access-token-xyz", "SPEAKER", "ada@oidc.test");

        var first = store.consumeTicket(ticket);
        assertTrue(first.isPresent());
        assertEquals("access-token-xyz", first.get().accessToken());
        assertEquals("SPEAKER", first.get().role());
        assertEquals("ada@oidc.test", first.get().email());

        assertTrue(store.consumeTicket(ticket).isEmpty());
    }

    @Test
    void expiredTicketIsRejected() {
        String ticket = store.putTicket("t", "SPEAKER", "e@x");
        now.addAndGet(OidcFlowStore.TICKET_TTL_MS + 1);
        assertTrue(store.consumeTicket(ticket).isEmpty());
    }

    @Test
    void loginAndTicketAreSeparateNamespaces() {
        store.putLogin("shared-key", "v", "n");
        // A ticket id is server-generated, but even a collision on the key space must not cross over.
        assertFalse(store.consumeTicket("shared-key").isPresent());
        assertTrue(store.consumeLogin("shared-key").isPresent());
    }
}
