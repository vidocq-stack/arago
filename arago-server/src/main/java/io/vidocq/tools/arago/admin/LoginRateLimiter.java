package io.vidocq.tools.arago.admin;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, virtual-thread-safe rate limiter for superadmin login attempts (cf. arago-spec §10.2):
 * at most {@value #DEFAULT_MAX} attempts per {@code key} (the client IP) per fixed 60s window. Beyond
 * that, {@link #allow} returns {@code false} and the resource replies {@code 429}.
 *
 * <p>Fixed-window counter kept atomic via {@link ConcurrentHashMap#compute} (no I/O under the bin
 * lock, so no virtual-thread pinning). Exponential lockout on consecutive failures is a later
 * refinement; the per-window cap already bounds brute force.</p>
 */
@ApplicationScoped
public class LoginRateLimiter {

    private static final int DEFAULT_MAX = 5;
    private static final long DEFAULT_WINDOW_NANOS = 60_000_000_000L; // 60s

    private final int maxPerWindow;
    private final long windowNanos;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public LoginRateLimiter() {
        this(DEFAULT_MAX, DEFAULT_WINDOW_NANOS);
    }

    LoginRateLimiter(int maxPerWindow, long windowNanos) {
        this.maxPerWindow = maxPerWindow;
        this.windowNanos = windowNanos;
    }

    /** Records an attempt for {@code key}; returns {@code false} once the per-window budget is exceeded. */
    public boolean allow(String key) {
        long now = System.nanoTime();
        Window w = windows.compute(key, (k, cur) ->
                (cur == null || now - cur.startNanos() > windowNanos)
                        ? new Window(now, 1)
                        : new Window(cur.startNanos(), cur.count() + 1));
        return w.count() <= maxPerWindow;
    }

    private record Window(long startNanos, int count) {}
}
