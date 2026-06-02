package io.vidocq.tools.arago.oidc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PKCE (RFC 7636) helpers for the OIDC Authorization Code flow — zero-dependency (JDK
 * {@link SecureRandom} + {@link MessageDigest}). The {@code code_verifier} is a 43-character
 * base64url string (32 random bytes); the {@code code_challenge} is {@code base64url(SHA-256(verifier))}
 * with the {@code S256} method. The verifier is kept server-side (see {@link OidcFlowStore}) and never
 * leaves the backend; only the challenge travels to Keycloak.
 */
public final class Pkce {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

    private Pkce() {}

    /** A PKCE pair: the secret {@code verifier} (kept server-side) and the public {@code challenge}. */
    public record Pair(String verifier, String challenge) {}

    /** Generates a fresh PKCE pair (S256). */
    public static Pair generate() {
        String verifier = randomUrlToken(32);
        return new Pair(verifier, challengeOf(verifier));
    }

    /** {@code code_challenge = base64url(SHA-256(ASCII(verifier)))}, no padding (RFC 7636 §4.2). */
    public static String challengeOf(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return B64URL.encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e); // never on a standard JDK
        }
    }

    /** A base64url (no padding) token from {@code numBytes} of secure randomness. */
    public static String randomUrlToken(int numBytes) {
        byte[] bytes = new byte[numBytes];
        RANDOM.nextBytes(bytes);
        return B64URL.encodeToString(bytes);
    }
}
