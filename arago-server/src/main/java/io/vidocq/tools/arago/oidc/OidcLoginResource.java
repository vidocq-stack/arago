package io.vidocq.tools.arago.oidc;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * OIDC front-channel: the Authorization Code + PKCE login flow (cf. arago-spec §7/§8). Mounted under
 * {@code /api} by Cassini.
 *
 * <ul>
 *   <li>{@code GET /api/oidc/login} — generates {@code state}/{@code nonce}/PKCE, stores them
 *       server-side, and 302-redirects the browser to Keycloak's authorization endpoint.</li>
 *   <li>{@code GET /api/oidc/callback} — validates+consumes {@code state}, exchanges the {@code code}
 *       for a Keycloak access token (PKCE), enforces the local allowlist (binding {@code sub} on first
 *       login), then 302s back to the SPA with a one-time {@code ticket} cookie (or an error param).</li>
 *   <li>{@code POST /api/oidc/token} — the SPA exchanges that single-use cookie ticket for the access
 *       token (JSON body), which it then sends as {@code Authorization: Bearer} (validated by cervantes).
 *       The token never travels in a URL.</li>
 * </ul>
 *
 * <p>These three endpoints are anonymous: cervantes' Bearer filter is inert without an
 * {@code Authorization} header, so the browser reaches them without a token.</p>
 */
@RequestScoped
@Path("/oidc")
public class OidcLoginResource {

    private static final System.Logger LOG = System.getLogger(OidcLoginResource.class.getName());
    private static final String TICKET_COOKIE = "arago_oidc_ticket";

    @Inject
    OidcFlowStore flow;

    @Inject
    KeycloakOidcClient keycloak;

    @Inject
    SpeakerAllowlist allowlist;

    @GET
    @Path("/login")
    public Response login(@QueryParam("return") String returnPath) {
        Pkce.Pair pkce = Pkce.generate();
        String state = Pkce.randomUrlToken(24);
        String nonce = Pkce.randomUrlToken(24);
        flow.putLogin(state, pkce.verifier(), nonce, sanitizeReturn(returnPath));
        return redirect(keycloak.authorizeUrl(state, nonce, pkce.challenge()));
    }

    /** A safe local return path (leading "/", no protocol-relative "//" or scheme) or "/" by default. */
    private static String sanitizeReturn(String returnPath) {
        if (returnPath == null || !returnPath.startsWith("/") || returnPath.startsWith("//")
                || returnPath.contains("://")) {
            return "/";
        }
        return returnPath;
    }

    @GET
    @Path("/callback")
    public Response callback(@QueryParam("code") String code,
            @QueryParam("state") String state,
            @QueryParam("error") String error) {
        if (error != null && !error.isBlank()) {
            return redirect("/?oidc_error=" + enc(error));
        }
        var login = flow.consumeLogin(state);
        if (login.isEmpty() || code == null || code.isBlank()) {
            return redirect("/?oidc_error=invalid_state");
        }

        String accessToken;
        try {
            accessToken = keycloak.exchangeCode(code, login.get().codeVerifier());
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "OIDC code exchange failed", e);
            return redirect("/?oidc_error=exchange_failed");
        }

        String returnPath = login.get().returnPath();
        JsonObject claims = JwtPayload.of(accessToken);
        var speaker = allowlist.authorize(JwtPayload.email(claims), JwtPayload.subject(claims));
        if (speaker.isEmpty()) {
            return redirect(returnPath + "?oidc_error=speaker_not_provisioned");
        }

        String ticket = flow.putTicket(
                accessToken, speaker.get().getRole().name(), speaker.get().getEmail());
        NewCookie cookie = new NewCookie.Builder(TICKET_COOKIE)
                .value(ticket)
                .path("/")
                .maxAge(60)
                .httpOnly(true)
                .secure(keycloak.publicBaseUrl().startsWith("https"))
                .sameSite(NewCookie.SameSite.LAX)
                .build();
        return Response.status(Response.Status.FOUND)
                .header(HttpHeaders.LOCATION, returnPath + "?login=ok")
                .cookie(cookie)
                .build();
    }

    @POST
    @Path("/token")
    @Produces(MediaType.APPLICATION_JSON)
    public Response token(@CookieParam(TICKET_COOKIE) String ticket) {
        var consumed = flow.consumeTicket(ticket);
        if (consumed.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        OidcFlowStore.Ticket t = consumed.get();
        NewCookie cleared = new NewCookie.Builder(TICKET_COOKIE)
                .value("").path("/").maxAge(0).httpOnly(true).build();
        return Response.ok(new SpeakerSession(t.accessToken(), t.role(), t.email()))
                .cookie(cleared)
                .build();
    }

    /** Single-use hand-off of the speaker's Keycloak access token + resolved identity to the SPA. */
    public record SpeakerSession(String accessToken, String role, String email) {}

    private static Response redirect(String location) {
        return Response.status(Response.Status.FOUND).header(HttpHeaders.LOCATION, location).build();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
