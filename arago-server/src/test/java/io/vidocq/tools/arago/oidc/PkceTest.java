package io.vidocq.tools.arago.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class PkceTest {

    @Test
    void challengeIsBase64UrlSha256OfVerifier() throws Exception {
        String verifier = Pkce.randomUrlToken(32);
        byte[] expectedDigest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII));
        String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(expectedDigest);

        assertEquals(expected, Pkce.challengeOf(verifier));
    }

    @Test
    void generatedPairIsConsistent() {
        Pkce.Pair pair = Pkce.generate();
        assertEquals(Pkce.challengeOf(pair.verifier()), pair.challenge());
    }

    @Test
    void verifierIsUrlSafeAndAtLeast43Chars() {
        String verifier = Pkce.generate().verifier();
        // 32 random bytes → 43 base64url chars (RFC 7636 allows 43..128).
        assertTrue(verifier.length() >= 43, () -> "verifier too short: " + verifier.length());
        assertTrue(verifier.matches("[A-Za-z0-9\\-_]+"), () -> "non-url-safe verifier: " + verifier);
    }

    @Test
    void tokensAreRandom() {
        assertNotEquals(Pkce.randomUrlToken(32), Pkce.randomUrlToken(32));
    }
}
