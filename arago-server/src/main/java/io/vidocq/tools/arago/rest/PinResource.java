package io.vidocq.tools.arago.rest;

import io.vidocq.tools.arago.persistence.Pin;
import io.vidocq.tools.arago.persistence.PinRepository;
import io.vidocq.tools.arago.persistence.Room;
import io.vidocq.tools.arago.persistence.RoomRepository;
import io.vidocq.tools.arago.persistence.Speaker;
import io.vidocq.tools.arago.speaker.SpeakerAuthenticator;
import io.vidocq.tools.arago.ws.RoomSocket;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

/**
 * Unpin a content block (cf. arago-spec §4.4): {@code DELETE /api/pins/{pinId}}. Owner-only — the
 * pin's room must be owned by the authenticated, enabled speaker. On success the removal is broadcast
 * to the room's WebSocket peers ({@code {"type":"pin","action":"remove",…}}).
 *
 * <p>Separate from {@code RoomResource} because the delete path is rooted at {@code /pins}, not
 * {@code /rooms}. {@code @RequestScoped} for {@code @Context HttpHeaders} (the bearer token).</p>
 */
@RequestScoped
@Path("/pins")
public class PinResource {

    @Context
    HttpHeaders httpHeaders;

    @Inject
    PinRepository pinRepo;

    @Inject
    RoomRepository rooms;

    @Inject
    SpeakerAuthenticator speakerAuth;

    @Inject
    RoomSocket roomSocket;

    @DELETE
    @Path("/{pinId}")
    public Response delete(@PathParam("pinId") String pinId) {
        String ownerSub = requireProvisionedSpeaker();
        Pin pin = pinRepo.findById(pinId).orElse(null);
        if (pin == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Room room = rooms.findById(pin.getRoomId()).orElse(null);
        if (room == null || !ownerSub.equals(room.getOwnerSub())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        pinRepo.deleteById(pinId);
        roomSocket.broadcast(pin.getRoomId(), RoomSocket.pinEvent("remove", pin));
        return Response.noContent().build();
    }

    private String requireProvisionedSpeaker() {
        return speakerAuth.authenticate(httpHeaders.getHeaderString(HttpHeaders.AUTHORIZATION))
                .map(Speaker::getId)
                .orElseThrow(() -> new WebApplicationException(Response.Status.UNAUTHORIZED));
    }
}
