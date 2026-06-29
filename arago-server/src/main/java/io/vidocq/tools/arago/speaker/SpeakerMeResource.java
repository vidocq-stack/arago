package io.vidocq.tools.arago.speaker;

import io.vidocq.tools.arago.persistence.ChatMessage;
import io.vidocq.tools.arago.persistence.ChatMessageRepository;
import io.vidocq.tools.arago.persistence.Room;
import io.vidocq.tools.arago.persistence.RoomManager;
import io.vidocq.tools.arago.persistence.RoomManagerRepository;
import io.vidocq.tools.arago.persistence.RoomRepository;
import io.vidocq.tools.arago.persistence.Speaker;
import io.vidocq.tools.arago.persistence.SpeakerRepository;
import io.vidocq.tools.arago.ws.RoomSocket;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Speaker self-service identity (cf. arago-spec §4.2/§17.3). Mounted under {@code /api} by Cassini →
 * {@code GET /api/speaker/me} and {@code PUT /api/speaker/me/pseudo}. Requires a valid speaker token
 * ({@link SpeakerAuthenticator}); {@code 401} otherwise.
 */
@RequestScoped
@Path("/speaker")
public class SpeakerMeResource {

    @Inject
    SpeakerAuthenticator authenticator;

    @Inject
    SpeakerRepository speakers;

    @Inject
    ChatMessageRepository messages;

    @Inject
    RoomRepository rooms;

    @Inject
    RoomManagerRepository managers;

    @Inject
    RoomSocket roomSocket;

    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    public Response me(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
        return authenticator.authenticate(authorization)
                .map(s -> Response.ok(view(s)).build())
                .orElseGet(() -> unauthorized());
    }

    /**
     * Sets the speaker's pseudo (§17.3) — re-suffixed with a unique {@code #nnn}. It becomes their chat
     * author name and the handle by which the room owner invites them as a co-speaker. The change is
     * propagated live + future + historical: past speaker chat messages are relabelled and a
     * {@code rename} frame is broadcast to every room the speaker manages. {@code 400} on a blank pseudo,
     * {@code 401} when unauthenticated.
     */
    @PUT
    @Path("/me/pseudo")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response setPseudo(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
                              PseudoRequest request) {
        Speaker speaker = authenticator.authenticate(authorization).orElse(null);
        if (speaker == null) {
            return unauthorized();
        }
        if (request == null || request.pseudo() == null || request.pseudo().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        String base = request.pseudo().trim().replaceAll("#\\d+$", ""); // drop any #nnn the user typed
        if (base.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        if (base.length() > 40) {
            base = base.substring(0, 40);
        }
        String oldPseudo = speaker.getPseudo();
        String newPseudo = uniquePseudo(base);
        speaker.setPseudo(newPseudo);
        speakers.save(speaker);

        if (oldPseudo != null && !oldPseudo.equals(newPseudo)) {
            propagatePseudoChange(speaker, oldPseudo, newPseudo);
        }
        return Response.ok(view(speaker)).build();
    }

    /**
     * Live + future + historical propagation of a speaker pseudo change. Historical: rewrites the
     * denormalised {@code author_pseudo} on every past speaker message (reliable because speaker pseudos
     * are globally unique). Live: broadcasts a {@code rename} frame to each room the speaker owns or
     * co-manages so connected clients relabel chat authors immediately.
     */
    private void propagatePseudoChange(Speaker speaker, String oldPseudo, String newPseudo) {
        for (ChatMessage m : messages.findByAuthorPseudoAndFromSpeakerTrue(oldPseudo)) {
            m.setAuthorPseudo(newPseudo);
            messages.save(m);
        }
        Set<String> roomIds = new LinkedHashSet<>();
        for (Room r : rooms.findByOwnerSubOrderByCreatedAtDesc(speaker.getId())) {
            roomIds.add(r.getId());
        }
        if (speaker.getEmail() != null) {
            for (RoomManager rm : managers.findBySpeakerEmail(speaker.getEmail())) {
                roomIds.add(rm.getRoomId());
            }
        }
        for (String roomId : roomIds) {
            roomSocket.broadcastSpeakerRename(roomId, oldPseudo, newPseudo);
        }
    }

    /** {@code base#nnn} with a 3-digit suffix unique across speakers (retries, then falls back to a UUID tag). */
    private String uniquePseudo(String base) {
        for (int i = 0; i < 20; i++) {
            String candidate = base + "#" + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
            if (speakers.findByPseudo(candidate).isEmpty()) {
                return candidate;
            }
        }
        return base + "#" + UUID.randomUUID().toString().substring(0, 4);
    }

    private static Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").build();
    }

    /** Identity payload for a speaker (also reused by {@link SpeakerAuth} on login). */
    static MeView view(Speaker s) {
        return new MeView(s.getEmail(), s.getRole().name(), s.getId(), s.getPseudo(), s.getDisplayName());
    }
}
