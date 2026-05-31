package io.vidocq.tools.arago.admin;

import io.vidocq.tools.arago.auth.AragoJwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Verifies the superadmin Bearer token on protected admin endpoints (cf. arago-spec §4.8/§10.2).
 * The token is the HS256 token minted by {@code AdminLoginResource} ({@code aud=arago-admin},
 * {@code role=superadmin}). Any failure (missing/invalid/expired token, wrong audience or role)
 * yields {@code 401}.
 *
 * <p>Reads the signing secret via {@link ConfigProvider} (same key as the issuer), so issuance and
 * verification stay in sync without a shared bean. Returns an empty {@link Optional} on any failure
 * (the resource then returns {@code 401}); it does not throw, so the outcome is an explicit response
 * rather than a mapped exception.</p>
 */
@ApplicationScoped
public class AdminAuthenticator {

    private volatile AragoJwt jwt;

    /** Validates the {@code Authorization} header; empty on any failure (missing/invalid/expired/wrong role). */
    public Optional<AragoJwt.Claims> authenticate(String authorizationHeader) {
        AragoJwt verifier = jwt();
        if (verifier == null) {
            return Optional.empty(); // admin auth not configured
        }
        String token = bearerToken(authorizationHeader);
        if (token == null) {
            return Optional.empty();
        }
        try {
            AragoJwt.Claims claims = verifier.verify(token, AragoJwt.AUDIENCE_ADMIN);
            return "superadmin".equals(claims.role()) ? Optional.of(claims) : Optional.empty();
        } catch (AragoJwt.InvalidTokenException e) {
            return Optional.empty();
        }
    }

    private static String bearerToken(String header) {
        if (header == null) {
            return null;
        }
        if (header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String t = header.substring(7).trim();
            return t.isEmpty() ? null : t;
        }
        return null;
    }

    private AragoJwt jwt() {
        AragoJwt j = jwt;
        if (j == null) {
            synchronized (this) {
                j = jwt;
                if (j == null) {
                    String secret = ConfigProvider.getConfig()
                            .getOptionalValue("arago.attendee.hmac-secret", String.class).orElse(null);
                    if (secret != null && !secret.isBlank()) {
                        jwt = j = new AragoJwt(secret.getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        }
        return j;
    }
}
