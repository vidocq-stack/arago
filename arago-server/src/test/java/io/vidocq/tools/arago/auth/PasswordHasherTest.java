package io.vidocq.tools.arago.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage of {@link PasswordHasher} (PBKDF2-HMAC-SHA256, zero-dep — cf. arago-spec §5).
 * Uses a low iteration count for speed; production defaults to 600k.
 */
class PasswordHasherTest {

    // Keep tests fast — the iteration count is irrelevant to correctness, only to cost.
    private final PasswordHasher hasher = new PasswordHasher(1_000);

    @Test
    void hash_then_verify_succeeds_for_correct_password() {
        String stored = hasher.hash("correct horse battery staple".toCharArray());
        assertTrue(hasher.verify("correct horse battery staple".toCharArray(), stored));
    }

    @Test
    void verify_fails_for_wrong_password() {
        String stored = hasher.hash("s3cr3t".toCharArray());
        assertFalse(hasher.verify("S3cr3t".toCharArray(), stored));
        assertFalse(hasher.verify("".toCharArray(), stored));
    }

    @Test
    void hash_uses_a_fresh_salt_each_time() {
        char[] pw = "same-password".toCharArray();
        assertNotEquals(hasher.hash(pw.clone()), hasher.hash(pw.clone()),
                "two hashes of the same password must differ (random salt)");
    }

    @Test
    void hash_is_self_describing_phc_like_format() {
        String stored = new PasswordHasher(600_000).hash("x".toCharArray());
        // $pbkdf2-sha256$i=600000$<saltB64>$<hashB64>
        assertTrue(stored.startsWith("$pbkdf2-sha256$i=600000$"), stored);
        assertEquals(5, stored.split("\\$").length, stored);
    }

    @Test
    void verify_honours_iterations_encoded_in_the_string() {
        // Hash with 1000 iterations, verify with a hasher configured for a different default:
        // the iteration count must come from the stored string, not the verifier instance.
        String stored = new PasswordHasher(1_000).hash("pw".toCharArray());
        assertTrue(new PasswordHasher(999_999).verify("pw".toCharArray(), stored));
    }

    @Test
    void verify_rejects_malformed_or_unknown_hash() {
        assertThrows(IllegalArgumentException.class, () -> hasher.verify("pw".toCharArray(), null));
        assertThrows(IllegalArgumentException.class, () -> hasher.verify("pw".toCharArray(), "nonsense"));
        assertThrows(IllegalArgumentException.class,
                () -> hasher.verify("pw".toCharArray(), "$argon2id$v=19$m=1$abc$def"));
        assertThrows(IllegalArgumentException.class,
                () -> hasher.verify("pw".toCharArray(), "$pbkdf2-sha256$i=NaN$c2FsdA$aGFzaA"));
    }

    @Test
    void constructor_rejects_non_positive_iterations() {
        assertThrows(IllegalArgumentException.class, () -> new PasswordHasher(0));
    }
}
