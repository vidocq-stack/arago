package io.vidocq.tools.arago.acceptance;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-scenario shared state, injected into every step class by cucumber-picocontainer. Lets a REST
 * step capture a value (e.g. a room PIN via "I remember …") and a UI step read it back when driving
 * the browser — the two glue classes are distinct instances but share this one {@code World}.
 */
public class World {

    /** Values captured from responses, substituted into later paths/inputs. */
    public final Map<String, String> vars = new HashMap<>();
}
