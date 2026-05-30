package io.vidocq.tools.arago.rest;

import io.vidocq.tools.arago.persistence.RoomRepository;
import io.vidocq.tools.arago.persistence.RoomStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Minimal Phase 0 resource: exercises Cassini (JAX-RS), Vauban (CDI injection) and Mansart
 * (the generated {@link RoomRepository}) in one read path. Mounted under {@code /api} by the
 * Cassini extension, so the effective path is {@code GET /api/rooms/count}.
 *
 * <p>Room creation, join, moderation, etc. arrive in Phase 1+ (cf. arago-spec §8).
 */
@ApplicationScoped
@Path("/rooms")
public class RoomResource {

    @Inject
    RoomRepository rooms;

    @GET
    @Path("/count")
    @Produces(MediaType.APPLICATION_JSON)
    public RoomCounts count() {
        return new RoomCounts(rooms.count(), rooms.countByStatus(RoomStatus.ACTIVE));
    }
}
