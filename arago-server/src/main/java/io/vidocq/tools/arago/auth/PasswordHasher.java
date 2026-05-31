package io.vidocq.tools.arago.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Password hashing for the single superadmin account (cf. arago-spec §5/§10.2).
 *
 * <p><strong>PBKDF2-HMAC-SHA256, 100% JDK, zero dependency</strong> — deliberate choice (2026-05-31)
 * to honour the Vidocq zero-dep philosophy (the JDK ships no Argon2). The stored hash is a
 * self-describing, PHC-like string:</p>
 *
 * <pre>$pbkdf2-sha256$i=&lt;iterations&gt;$&lt;saltB64&gt;$&lt;hashB64&gt;</pre>
 *
 * <p>Because the algorithm and parameters are encoded in the string, the format is forward-compatible:
 * a future move to Argon2id would add a new {@code $argon2id$…} variant without changing the
 * {@code ARAGO_SUPERADMIN_PASSWORD_HASH} contract.</p>
 *
 * <p>The clear-text password never leaves a {@code char[]} and is never logged. Verification is
 * constant-time ({@link MessageDigest#isEqual}).</p>
 *
 * <p>This is a stateless pure utility (no CDI, no framework coupling) so it is trivially unit-tested
 * and reusable by the {@code arago hash-password} CLI.</p>
 */
public final class PasswordHasher {

    /** PHC-like scheme identifier for this algorithm. */
    static final String SCHEME = "pbkdf2-sha256";

    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int DEFAULT_ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256; // SHA-256 output width

    private final SecureRandom random = new SecureRandom();
    private final int iterations;

    public PasswordHasher() {
        this(DEFAULT_ITERATIONS);
    }

    public PasswordHasher(int iterations) {
        if (iterations < 1) {
            throw new IllegalArgumentException("iterations must be >= 1");
        }
        this.iterations = iterations;
    }

    /**
     * Hashes {@code password} with a fresh random salt, returning a self-describing PHC-like string.
     * The caller should clear the {@code char[]} afterwards.
     */
    public String hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] derived = pbkdf2(password, salt, iterations);
        Base64.Encoder b64 = Base64.getEncoder().withoutPadding();
        return "$" + SCHEME + "$i=" + iterations
                + "$" + b64.encodeToString(salt)
                + "$" + b64.encodeToString(derived);
    }

    /**
     * Verifies {@code password} against a stored PHC-like {@code stored} string, constant-time.
     *
     * @throws IllegalArgumentException if {@code stored} is malformed or uses an unknown scheme
     *         (a configuration error — distinct from a wrong password, which returns {@code false}).
     */
    public boolean verify(char[] password, String stored) {
        Parsed p = Parsed.of(stored);
        byte[] candidate = pbkdf2(password, p.salt(), p.iterations());
        return MessageDigest.isEqual(candidate, p.hash());
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).getEncoded();
        } catch (java.security.NoSuchAlgorithmException | InvalidKeySpecException e) {
            // PBKDF2WithHmacSHA256 is mandated by the JDK spec — unreachable on a conformant runtime.
            throw new IllegalStateException("PBKDF2 unavailable", e);
        } finally {
            spec.clearPassword();
        }
    }

    /** Parsed components of a {@code $pbkdf2-sha256$i=…$salt$hash} string. */
    private record Parsed(int iterations, byte[] salt, byte[] hash) {

        static Parsed of(String stored) {
            if (stored == null) {
                throw new IllegalArgumentException("password hash is null");
            }
            // Leading '$' yields an empty first segment, hence 5 parts.
            String[] parts = stored.split("\\$");
            if (parts.length != 5 || !parts[0].isEmpty() || !SCHEME.equals(parts[1])) {
                throw new IllegalArgumentException("unsupported or malformed password hash");
            }
            if (!parts[2].startsWith("i=")) {
                throw new IllegalArgumentException("malformed iteration parameter");
            }
            int iter;
            try {
                iter = Integer.parseInt(parts[2].substring(2));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("malformed iteration count", e);
            }
            if (iter < 1) {
                throw new IllegalArgumentException("iteration count must be >= 1");
            }
            Base64.Decoder b64 = Base64.getDecoder();
            try {
                return new Parsed(iter, b64.decode(parts[3]), b64.decode(parts[4]));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("malformed salt or hash encoding", e);
            }
        }
    }
}
