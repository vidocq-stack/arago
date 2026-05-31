package io.vidocq.tools.arago.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage of {@link AragoJwt} (hand-rolled HS256, zero-dep — cf. arago-spec §4.2/§10).
 */
class AragoJwtTest {

    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final Instant T0 = Instant.parse("2026-05-31T10:00:00Z");

    private final AragoJwt jwt = new AragoJwt(SECRET, Clock.fixed(T0, ZoneOffset.UTC));

    @Test
    void issue_then_verify_round_trips_claims() {
        String token = jwt.issue("root", "superadmin", AragoJwt.AUDIENCE_ADMIN,
                Duration.ofMinutes(30), Map.of());
        AragoJwt.Claims c = jwt.verify(token, AragoJwt.AUDIENCE_ADMIN);
        assertEquals("root", c.subject());
        assertEquals("superadmin", c.role());
        assertEquals(AragoJwt.ISSUER, c.issuer());
        assertEquals(AragoJwt.AUDIENCE_ADMIN, c.audience());
        assertEquals(T0.plus(Duration.ofMinutes(30)).getEpochSecond(), c.expiresAt().getEpochSecond());
    }

    @Test
    void extra_claims_survive_round_trip() {
        String token = jwt.issue("att-123", "attendee", AragoJwt.AUDIENCE_ATTENDEE,
                Duration.ofHours(13), Map.of("roomId", "room-42", "pseudo", "Ada"));
        AragoJwt.Claims c = jwt.verify(token, AragoJwt.AUDIENCE_ATTENDEE);
        assertEquals("room-42", c.extra().get("roomId"));
        assertEquals("Ada", c.extra().get("pseudo"));
    }

    @Test
    void verify_rejects_wrong_audience() {
        String token = jwt.issue("root", "superadmin", AragoJwt.AUDIENCE_ADMIN, Duration.ofMinutes(30), Map.of());
        assertThrows(AragoJwt.InvalidTokenException.class,
                () -> jwt.verify(token, AragoJwt.AUDIENCE_ATTENDEE));
    }

    @Test
    void verify_rejects_expired_token() {
        String token = jwt.issue("root", "superadmin", AragoJwt.AUDIENCE_ADMIN, Duration.ofMinutes(30), Map.of());
        AragoJwt later = new AragoJwt(SECRET, Clock.fixed(T0.plus(Duration.ofMinutes(31)), ZoneOffset.UTC));
        assertThrows(AragoJwt.InvalidTokenException.class,
                () -> later.verify(token, AragoJwt.AUDIENCE_ADMIN));
    }

    @Test
    void verify_rejects_tampered_payload() {
        String token = jwt.issue("root", "superadmin", AragoJwt.AUDIENCE_ADMIN, Duration.ofMinutes(30), Map.of());
        String[] p = token.split("\\.");
        // flip the last char of the payload segment
        char[] body = p[1].toCharArray();
        body[body.length - 1] = (body[body.length - 1] == 'A') ? 'B' : 'A';
        String tampered = p[0] + "." + new String(body) + "." + p[2];
        assertThrows(AragoJwt.InvalidTokenException.class,
                () -> jwt.verify(tampered, AragoJwt.AUDIENCE_ADMIN));
    }

    @Test
    void verify_rejects_wrong_secret() {
        String token = jwt.issue("root", "superadmin", AragoJwt.AUDIENCE_ADMIN, Duration.ofMinutes(30), Map.of());
        AragoJwt other = new AragoJwt("ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ".getBytes(StandardCharsets.UTF_8),
                Clock.fixed(T0, ZoneOffset.UTC));
        assertThrows(AragoJwt.InvalidTokenException.class,
                () -> other.verify(token, AragoJwt.AUDIENCE_ADMIN));
    }

    @Test
    void verify_rejects_alg_none_and_algorithm_confusion() {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String header = b64.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String body = b64.encodeToString(("{\"iss\":\"arago\",\"aud\":\"arago-admin\",\"sub\":\"root\","
                + "\"role\":\"superadmin\",\"exp\":9999999999}").getBytes(StandardCharsets.UTF_8));
        String forged = header + "." + body + ".";
        assertThrows(AragoJwt.InvalidTokenException.class,
                () -> jwt.verify(forged, AragoJwt.AUDIENCE_ADMIN));
    }

    @Test
    void verify_rejects_malformed_token() {
        assertThrows(AragoJwt.InvalidTokenException.class, () -> jwt.verify(null, AragoJwt.AUDIENCE_ADMIN));
        assertThrows(AragoJwt.InvalidTokenException.class, () -> jwt.verify("a.b", AragoJwt.AUDIENCE_ADMIN));
        assertThrows(AragoJwt.InvalidTokenException.class, () -> jwt.verify("not-a-jwt", AragoJwt.AUDIENCE_ADMIN));
    }

    @Test
    void constructor_rejects_short_secret() {
        assertThrows(IllegalArgumentException.class, () -> new AragoJwt("tooshort".getBytes(StandardCharsets.UTF_8)));
    }
}
