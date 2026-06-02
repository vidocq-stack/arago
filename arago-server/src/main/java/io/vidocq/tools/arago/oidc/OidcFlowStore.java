package io.vidocq.tools.arago.oidc;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Short-lived, single-use server-side state for the OIDC Authorization Code + PKCE flow. No session:
 * just two self-expiring maps, both consumed exactly once.
 *
 * <ul>
 *   <li><b>login</b> ({@code state → (codeVerifier, nonce)}, TTL 5 min) — bridges {@code /oidc/login}
 *       and {@code /oidc/callback}; the {@code code_verifier} never leaves the backend.</li>
 *   <li><b>ticket</b> ({@code ticket → (accessToken, role, email)}, TTL 60 s) — one-time hand-off of the
 *       Keycloak access token to the SPA via {@code POST /oidc/token}, so the token never rides a URL.</li>
 * </ul>
 *
 * <p>Backed by {@link ConcurrentHashMap}; {@code consume*} uses {@link Map#remove(Object)} so a value is
 * delivered to at most one caller (virtual-thread-safe, no locking). Entries are purged lazily on write.</p>
 */
@ApplicationScoped
public class OidcFlowStore {

    /** Pending login: the PKCE verifier and nonce bound to a {@code state}, with its creation instant. */
    public record Login(String codeVerifier, String nonce, long createdAtMillis) {}

    /** One-time hand-off of the resolved access token + speaker identity to the SPA. */
    public record Ticket(String accessToken, String role, String email, long createdAtMillis) {}

    static final long LOGIN_TTL_MS = 5 * 60 * 1000L;
    static final long TICKET_TTL_MS = 60 * 1000L;

    private final Map<String, Login> logins = new ConcurrentHashMap<>();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public OidcFlowStore() {
        this(System::currentTimeMillis);
    }

    /** Test seam: a controllable millisecond clock to exercise TTL expiry deterministically. */
    OidcFlowStore(LongSupplier clock) {
        this.clock = clock;
    }

    /** Registers a pending login keyed by {@code state}; returns nothing — call {@link #consumeLogin}. */
    public void putLogin(String state, String codeVerifier, String nonce) {
        purge();
        logins.put(state, new Login(codeVerifier, nonce, clock.getAsLong()));
    }

    /** Atomically removes and returns the login for {@code state}, or empty if absent/expired. */
    public Optional<Login> consumeLogin(String state) {
        if (state == null) {
            return Optional.empty();
        }
        Login login = logins.remove(state);
        if (login == null || expired(login.createdAtMillis(), LOGIN_TTL_MS)) {
            return Optional.empty();
        }
        return Optional.of(login);
    }

    /** Stores the access token + identity and returns a fresh single-use ticket id. */
    public String putTicket(String accessToken, String role, String email) {
        purge();
        String ticket = Pkce.randomUrlToken(24);
        tickets.put(ticket, new Ticket(accessToken, role, email, clock.getAsLong()));
        return ticket;
    }

    /** Atomically removes and returns the ticket payload, or empty if absent/expired. */
    public Optional<Ticket> consumeTicket(String ticket) {
        if (ticket == null) {
            return Optional.empty();
        }
        Ticket value = tickets.remove(ticket);
        if (value == null || expired(value.createdAtMillis(), TICKET_TTL_MS)) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private boolean expired(long createdAtMillis, long ttlMillis) {
        return clock.getAsLong() - createdAtMillis > ttlMillis;
    }

    private void purge() {
        long now = clock.getAsLong();
        logins.entrySet().removeIf(e -> now - e.getValue().createdAtMillis() > LOGIN_TTL_MS);
        tickets.entrySet().removeIf(e -> now - e.getValue().createdAtMillis() > TICKET_TTL_MS);
    }
}
