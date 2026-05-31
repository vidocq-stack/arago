package io.vidocq.tools.arago.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Pure authentication logic for the single break-glass superadmin account (cf. arago-spec §4.8/§10.2).
 * No CDI, no JAX-RS — trivially unit-tested; the resource layer feeds it the configured values.
 *
 * <p>Login is available only when BOTH a password hash and a signing secret are configured; otherwise
 * {@link #enabled()} is {@code false} and every login fails (the endpoint then reports "disabled").
 * Failures are uniform (no distinction between unknown user and wrong password). On success a short
 * HS256 token with {@code role=superadmin}, {@code aud=arago-admin} is issued.</p>
 */
public final class SuperadminAuth {

    private final String username;
    private final String passwordHash;   // PHC string; null/blank => disabled
    private final Duration tokenTtl;
    private final PasswordHasher hasher = new PasswordHasher();
    private final AragoJwt jwt;           // null if no secret => disabled

    public SuperadminAuth(String username, String passwordHash, int tokenTtlMinutes, String hmacSecret) {
        this.username = blankToNull(username);
        this.passwordHash = blankToNull(passwordHash);
        this.tokenTtl = Duration.ofMinutes(tokenTtlMinutes > 0 ? tokenTtlMinutes : 30);
        String secret = blankToNull(hmacSecret);
        this.jwt = secret == null ? null : new AragoJwt(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Login is available only when a username, a password hash and a signing secret are all set. */
    public boolean enabled() {
        return username != null && passwordHash != null && jwt != null;
    }

    /**
     * Verifies credentials and, on success, returns a signed superadmin token. Returns empty on any
     * failure (disabled, unknown user, wrong password) — the caller must not leak which.
     */
    public Optional<String> login(String user, String password) {
        if (!enabled()) {
            return Optional.empty();
        }
        boolean userOk = constantTimeEquals(username, user);
        boolean passOk;
        try {
            char[] pw = (password == null ? new char[0] : password.toCharArray());
            passOk = hasher.verify(pw, passwordHash);
        } catch (RuntimeException e) {
            passOk = false; // malformed stored hash is a config error — fail closed
        }
        if (userOk && passOk) {
            return Optional.of(jwt.issue(username, "superadmin", AragoJwt.AUDIENCE_ADMIN, tokenTtl, Map.of()));
        }
        return Optional.empty();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
