package io.vidocq.tools.arago.ws;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Periodic WebSocket keepalive: pings every open room socket ({@link RoomSocket#pingAll()}) so that
 * reverse proxies never see the connection as idle. Without it, a silent room (no chat, no seat move)
 * hits the proxy read timeout — Nginx / Nginx Proxy Manager defaults to 60s — and every attendee is
 * dropped mid-session. The interval is {@code arago.ws.ping-seconds} (default 25s, safely under the
 * 60s default); {@code 0} or a negative value disables the keepalive (useful in tests).
 *
 * <p><strong>Temporary stopgap</strong>, same as {@code PurgeScheduler}: a hand-rolled single
 * daemon-thread {@link ScheduledExecutorService}, to be replaced by a declarative {@code @Scheduled}
 * method once the Vidocq concurrency spec ships its extension.</p>
 */
@ApplicationScoped
public class WsPingScheduler {

    private static final System.Logger LOG = System.getLogger(WsPingScheduler.class.getName());
    private static final long DEFAULT_INTERVAL_SECONDS = 25;

    private final RoomSocket roomSocket;
    private ScheduledExecutorService scheduler;

    @Inject
    public WsPingScheduler(RoomSocket roomSocket) {
        this.roomSocket = roomSocket;
    }

    void onStart(@Observes @Initialized(ApplicationScoped.class) Object event) {
        long seconds = ConfigProvider.getConfig()
                .getOptionalValue("arago.ws.ping-seconds", Long.class).orElse(DEFAULT_INTERVAL_SECONDS);
        if (seconds <= 0) {
            LOG.log(System.Logger.Level.INFO, "WebSocket keepalive disabled (arago.ws.ping-seconds={0})", seconds);
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "arago-ws-ping");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::safePing, seconds, seconds, TimeUnit.SECONDS);
        LOG.log(System.Logger.Level.INFO, "WebSocket keepalive scheduler started (every {0}s)", seconds);
    }

    @PreDestroy
    void onShutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * Runs one keepalive round, never propagating: a task that throws from {@code scheduleAtFixedRate}
     * silently cancels all future runs, so the failure is caught and logged instead.
     */
    void safePing() {
        try {
            roomSocket.pingAll();
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "WebSocket keepalive round failed; will retry next interval", e);
        }
    }
}
