package io.vidocq.tools.arago.profile;

import io.vidocq.tools.arago.admin.LoginRateLimiter;
import io.vidocq.tools.arago.auth.AragoJwt;
import io.vidocq.tools.arago.mail.Mailer;
import io.vidocq.tools.arago.persistence.AttendeeProfileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.eclipse.microprofile.config.ConfigProvider;

/**
 * RGPD attendee self-service (cf. arago-spec §4.7), mounted under {@code /api} by Cassini. All access is
 * via a short-lived <em>magic-link</em> token (no attendee password, ever): {@code POST /magic-link}
 * issues one to a known email; the data-rights endpoints verify it.
 *
 * <ul>
 *   <li>{@code POST /api/profile/magic-link} {@code {email}} → always {@code 202} (anti-enumeration);
 *       a real {@link Mailer} sends the link. When {@code arago.mail.dev-expose-link=true} (dev/test) and
 *       the email is known, the link + token are also returned in the body.</li>
 *   <li>{@code GET /api/profile/me?token=} → profile + persistent messages (right of access).</li>
 *   <li>{@code GET /api/profile/me/export?token=} → same payload as a downloadable attachment (portability).</li>
 *   <li>{@code DELETE /api/profile/me?token=} → erasure (anonymise messages + delete profile).</li>
 * </ul>
 *
 * <p>Anonymous to cervantes: these endpoints carry no {@code Authorization: Bearer}, so the OIDC filter
 * stays inert and the magic-link token (a distinct {@code aud=arago-profile} HS256 token) is used instead.</p>
 */
@ApplicationScoped
@Path("/profile")
public class ProfileResource {

    @Inject
    AttendeeProfileRepository profiles;

    @Inject
    ProfileTokens tokens;

    @Inject
    ProfileDataService data;

    @Inject
    Mailer mailer;

    @Inject
    LoginRateLimiter rateLimiter;

    @POST
    @Path("/magic-link")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response magicLink(MagicLinkRequest request) {
        String email = request == null || request.email() == null ? null : request.email().trim().toLowerCase();
        if (email == null || email.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        // Throttle per email (independent of whether it exists — no enumeration signal).
        if (!rateLimiter.allow("magic-link:" + email)) {
            return Response.status(429).build();
        }

        Optional<String> profileId = profiles.findByEmail(email).map(p -> p.getId());
        if (profileId.isPresent()) {
            String token = tokens.issue(profileId.get());
            String link = publicBaseUrl() + "/api/profile/me?token="
                    + URLEncoder.encode(token, StandardCharsets.UTF_8);
            mailer.sendMagicLink(email, link);
            if (devExposeLink()) {
                return Response.status(Response.Status.ACCEPTED).entity(new DevLink(link, token)).build();
            }
        }
        // Always 202, identical shape whether or not the email is known (anti-enumeration).
        return Response.status(Response.Status.ACCEPTED).build();
    }

    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    public Response me(@QueryParam("token") String token) {
        String profileId = subject(token);
        if (profileId == null) {
            return unauthorized();
        }
        return data.myData(profileId)
                .map(d -> Response.ok(d).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/me/export")
    @Produces(MediaType.APPLICATION_JSON)
    public Response export(@QueryParam("token") String token) {
        String profileId = subject(token);
        if (profileId == null) {
            return unauthorized();
        }
        return data.myData(profileId)
                .map(d -> Response.ok(d)
                        .header("Content-Disposition", "attachment; filename=\"arago-my-data.json\"")
                        .build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    public Response erase(@QueryParam("token") String token) {
        String profileId = subject(token);
        if (profileId == null) {
            return unauthorized();
        }
        return Response.ok(data.erase(profileId)).build();
    }

    /** Resolves the profile id from a magic-link token, or null if the token is missing/invalid/expired. */
    private String subject(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return tokens.verify(token).subject();
        } catch (AragoJwt.InvalidTokenException e) {
            return null;
        }
    }

    private static Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    private static String publicBaseUrl() {
        String url = ConfigProvider.getConfig()
                .getOptionalValue("arago.public.url", String.class).orElse("http://localhost:8080");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static boolean devExposeLink() {
        return ConfigProvider.getConfig()
                .getOptionalValue("arago.mail.dev-expose-link", Boolean.class).orElse(Boolean.FALSE);
    }

    /** Magic-link request body. */
    public record MagicLinkRequest(String email) {}

    /** Dev/test-only echo of the issued link + token (never returned in production). */
    public record DevLink(String link, String token) {}
}
