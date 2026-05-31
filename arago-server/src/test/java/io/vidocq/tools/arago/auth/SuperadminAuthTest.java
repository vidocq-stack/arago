package io.vidocq.tools.arago.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage of {@link SuperadminAuth} (cf. arago-spec §4.8/§10.2).
 */
class SuperadminAuthTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef"; // 32 bytes
    // Hash with a low iteration count for test speed; verify honours the iterations in the PHC string.
    private static final String HASH = new PasswordHasher(1_000).hash("correct-horse".toCharArray());

    @Test
    void enabled_only_when_user_hash_and_secret_are_all_set() {
        assertTrue(new SuperadminAuth("root", HASH, 30, SECRET).enabled());
        assertFalse(new SuperadminAuth("root", null, 30, SECRET).enabled(), "no hash");
        assertFalse(new SuperadminAuth("root", HASH, 30, null).enabled(), "no secret");
        assertFalse(new SuperadminAuth(" ", HASH, 30, SECRET).enabled(), "blank user");
    }

    @Test
    void valid_credentials_yield_a_verifiable_superadmin_token() {
        SuperadminAuth auth = new SuperadminAuth("root", HASH, 30, SECRET);
        Optional<String> token = auth.login("root", "correct-horse");
        assertTrue(token.isPresent());

        AragoJwt.Claims c = new AragoJwt(SECRET.getBytes(StandardCharsets.UTF_8))
                .verify(token.get(), AragoJwt.AUDIENCE_ADMIN);
        assertEquals("superadmin", c.role());
        assertEquals("root", c.subject());
    }

    @Test
    void wrong_password_or_user_is_rejected() {
        SuperadminAuth auth = new SuperadminAuth("root", HASH, 30, SECRET);
        assertTrue(auth.login("root", "nope").isEmpty());
        assertTrue(auth.login("admin", "correct-horse").isEmpty());
        assertTrue(auth.login("root", null).isEmpty());
    }

    @Test
    void disabled_auth_never_logs_in() {
        SuperadminAuth disabled = new SuperadminAuth("root", null, 30, SECRET);
        assertTrue(disabled.login("root", "correct-horse").isEmpty());
    }
}
