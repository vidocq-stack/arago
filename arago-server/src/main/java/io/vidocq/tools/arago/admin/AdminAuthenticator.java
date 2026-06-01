package io.vidocq.tools.arago.admin;

import io.vidocq.tools.arago.auth.AragoJwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Verifies the superadmin token on protected admin endpoints (cf. arago-spec §4.8/§10.2). The token is
 * the HS256 token minted by {@code AdminLoginResource} ({@code aud=arago-admin}, {@code role=superadmin}).
 *
 * <p><strong>Carried on the {@code X-Arago-Admin} header, NOT {@code Authorization: Bearer}.</strong> When
 * OIDC is enabled, cervantes/MP-JWT owns the {@code Authorization: Bearer} scheme and rejects (401) any
 * Bearer it cannot verify against its issuer — which would kill the superadmin's local HS256 token. Keeping
 * the superadmin token on a distinct header lets both auth authorities coexist (cervantes stays strict /
 * TCK-compliant; the superadmin Bearer is simply invisible to it). See arago/BUG.md ARAGO-004.</p>
 *
 * <p>Reads the signing secret via {@link ConfigProvider} (same key as the issuer). Returns an empty
 * {@link Optional} on any failure (the resource then returns {@code 401}); it does not throw.</p>
 */
@ApplicationScoped
public class AdminAuthenticator {

    private volatile AragoJwt jwt;

    /** Validates the {@code X-Arago-Admin} header value; empty on any failure (missing/invalid/expired/wrong role). */
    public Optional<AragoJwt.Claims> authenticate(String adminTokenHeader) {
        AragoJwt verifier = jwt();
        if (verifier == null) {
            return Optional.empty(); // admin auth not configured
        }
        String token = stripOptionalBearer(adminTokenHeader);
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

    /** Accepts the raw token, or a {@code Bearer <token>}-prefixed value, from the X-Arago-Admin header. */
    private static String stripOptionalBearer(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String v = header.trim();
        if (v.regionMatches(true, 0, "Bearer ", 0, 7)) {
            v = v.substring(7).trim();
        }
        return v.isEmpty() ? null : v;
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
