package io.vidocq.tools.arago.attachments;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

/**
 * Serves a stored attachment by its opaque id (cf. arago-spec §4.3/§4.4). Mounted under {@code /api} by
 * Cassini → {@code GET /api/attachments/{id}}. Public, like the lobby: the id is an unguessable UUID and
 * the chat/pins that reference it are already shown to attendees, so no token juggling is needed to load
 * an image. Images are served {@code inline}; everything else as a download ({@code attachment}) with a
 * neutral disposition to avoid in-browser execution.
 */
@RequestScoped
@Path("/attachments")
public class AttachmentResource {

    @Inject
    AttachmentStore store;

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") String id) {
        return store.load(id).map(a -> {
            boolean inlineImage = "image".equals(a.kind()) && a.contentType().startsWith("image/");
            String fn = sanitize(a.filename() == null ? id : a.filename());
            return Response.ok(a.data(), a.contentType())
                    .header("Content-Disposition", (inlineImage ? "inline" : "attachment")
                            + "; filename=\"" + fn + "\"")
                    .header("Cache-Control", "private, max-age=300")
                    .header("X-Content-Type-Options", "nosniff")
                    .build();
        }).orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    private static String sanitize(String name) {
        String s = name.replaceAll("[\\r\\n\"\\\\]", "_");
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
