package io.vidocq.tools.arago.auth;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal, hardened HS256 JWT issuer/verifier for tokens <em>signed by Arago itself</em>
 * (superadmin, speaker and attendee tokens — cf. arago-spec §4.2/§10). All Arago auth is now local
 * (no external OIDC), so every token is HS256 and verified here.
 *
 * <p><strong>Zero dependency</strong> — HMAC via {@link Mac} (JDK), JSON via Jakarta JSON-P
 * (champollion, already in the stack). Deliberate choice over a JWT library to honour the Vidocq
 * zero-dep philosophy.</p>
 *
 * <p>Hardening (the parts that actually matter for a hand-rolled verifier):</p>
 * <ul>
 *   <li><strong>Algorithm allow-list</strong>: only {@code HS256} is accepted; {@code none} and any
 *       asymmetric {@code alg} are rejected before signature processing (prevents alg-confusion).</li>
 *   <li><strong>Constant-time</strong> signature comparison ({@link MessageDigest#isEqual}).</li>
 *   <li>Signature recomputed over the exact received {@code header.payload} segments.</li>
 *   <li>Strict {@code iss}, {@code aud} and {@code exp} checks (clock-injectable for tests).</li>
 *   <li>Secret must be at least 32 bytes (HS256).</li>
 * </ul>
 */
public final class AragoJwt {

    /** Fixed issuer for every Arago-minted token. */
    public static final String ISSUER = "arago";
    /** Audience for the superadmin token (cf. §4.2). */
    public static final String AUDIENCE_ADMIN = "arago-admin";
    /** Audience for the local speaker token (cf. §4.2). */
    public static final String AUDIENCE_SPEAKER = "arago-speaker";
    /** Audience for attendee tokens (cf. §4.2). */
    public static final String AUDIENCE_ATTENDEE = "arago-attendee";
    /** Audience for attendee RGPD magic-link tokens (cf. §4.7). */
    public static final String AUDIENCE_PROFILE = "arago-profile";

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String JWT_ALG = "HS256";
    private static final int MIN_SECRET_BYTES = 32;

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final byte[] secret;
    private final Clock clock;

    public AragoJwt(byte[] secret) {
        this(secret, Clock.systemUTC());
    }

    public AragoJwt(byte[] secret, Clock clock) {
        if (secret == null || secret.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("HS256 secret must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        this.secret = secret.clone();
        this.clock = clock;
    }

    /** Validated claims of an Arago token. */
    public record Claims(String subject, String issuer, String audience, String role,
                         Instant issuedAt, Instant expiresAt, Map<String, String> extra) {
    }

    /**
     * Issues a signed token. {@code iss} is fixed to {@value #ISSUER}; {@code iat}/{@code exp} are
     * derived from the clock and {@code ttl}. {@code extra} string claims (e.g. {@code roomId}) are
     * merged into the payload.
     */
    public String issue(String subject, String role, String audience, Duration ttl, Map<String, String> extra) {
        Instant now = clock.instant();
        Instant exp = now.plus(ttl);

        var payload = Json.createObjectBuilder()
                .add("iss", ISSUER)
                .add("sub", subject)
                .add("aud", audience)
                .add("role", role)
                .add("iat", now.getEpochSecond())
                .add("exp", exp.getEpochSecond());
        if (extra != null) {
            extra.forEach(payload::add);
        }

        String header = B64.encodeToString(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String body = B64.encodeToString(payload.build().toString()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String signingInput = header + "." + body;
        return signingInput + "." + B64.encodeToString(hmac(signingInput));
    }

    /**
     * Verifies a token and returns its claims, or throws {@link InvalidTokenException} if the
     * signature, algorithm, issuer, audience or expiry is wrong/expired.
     */
    public Claims verify(String token, String expectedAudience) {
        if (token == null) {
            throw new InvalidTokenException("token is null");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new InvalidTokenException("token must have three segments");
        }
        String header = parts[0];
        String body = parts[1];

        // 1. Algorithm allow-list — reject before doing any signature work.
        JsonObject head = parseJson(header, "header");
        if (!JWT_ALG.equals(head.getString("alg", null))) {
            throw new InvalidTokenException("unsupported JWT alg (only HS256 accepted)");
        }

        // 2. Constant-time signature check over the exact received segments.
        byte[] expected = hmac(header + "." + body);
        byte[] provided;
        try {
            provided = B64D.decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("malformed signature encoding");
        }
        if (!MessageDigest.isEqual(expected, provided)) {
            throw new InvalidTokenException("bad signature");
        }

        // 3. Claims.
        JsonObject claims = parseJson(body, "payload");
        if (!ISSUER.equals(claims.getString("iss", null))) {
            throw new InvalidTokenException("bad issuer");
        }
        String aud = claims.getString("aud", null);
        if (!expectedAudience.equals(aud)) {
            throw new InvalidTokenException("bad audience");
        }
        long expEpoch = claims.getJsonNumber("exp") != null ? claims.getJsonNumber("exp").longValue() : 0L;
        Instant exp = Instant.ofEpochSecond(expEpoch);
        if (!clock.instant().isBefore(exp)) {
            throw new InvalidTokenException("token expired");
        }

        Map<String, String> extra = new LinkedHashMap<>();
        for (String key : claims.keySet()) {
            if (!RESERVED.contains(key)) {
                extra.put(key, claims.getString(key, ""));
            }
        }
        long iat = claims.getJsonNumber("iat") != null ? claims.getJsonNumber("iat").longValue() : 0L;
        return new Claims(claims.getString("sub", null), ISSUER, aud, claims.getString("role", null),
                Instant.ofEpochSecond(iat), exp, Map.copyOf(extra));
    }

    private static final java.util.Set<String> RESERVED =
            java.util.Set.of("iss", "sub", "aud", "role", "iat", "exp");

    private byte[] hmac(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(signingInput.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            // HmacSHA256 is mandated by the JDK spec — unreachable on a conformant runtime.
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static JsonObject parseJson(String segmentB64, String what) {
        try (JsonReader r = Json.createReader(new StringReader(
                new String(B64D.decode(segmentB64), java.nio.charset.StandardCharsets.UTF_8)))) {
            return r.readObject();
        } catch (RuntimeException e) {
            throw new InvalidTokenException("malformed " + what);
        }
    }

    /** Thrown when a token fails verification (signature, alg, issuer, audience, expiry, format). */
    public static final class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }
}
