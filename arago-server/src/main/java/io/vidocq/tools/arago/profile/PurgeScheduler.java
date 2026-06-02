package io.vidocq.tools.arago.profile;

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
 * Runs the RGPD retention purge ({@link PurgeService#run()}) periodically (cf. arago-spec §4.7, "job de
 * purge programmé quotidien").
 *
 * <p><strong>Temporary stopgap.</strong> This is a hand-rolled in-app scheduler: a single daemon-thread
 * {@link ScheduledExecutorService} started at container init and stopped at shutdown. It exists only
 * until the Vidocq <em>concurrency</em> spec ships its {@code @Scheduled} extension, at which point this
 * class is replaced by a declarative {@code @Scheduled} method. Kept deliberately minimal: a daily cadence
 * on one daemon thread (mirrors {@code cassini-async-timeout}) needs no thread pool nor a virtual-thread
 * dispatch. The manual {@code POST /api/admin/purge/run} endpoint remains the on-demand trigger.</p>
 */
@ApplicationScoped
public class PurgeScheduler {

    private static final System.Logger LOG = System.getLogger(PurgeScheduler.class.getName());
    private static final long DEFAULT_INTERVAL_MINUTES = 1440; // daily

    private final PurgeService purge;
    private ScheduledExecutorService scheduler;

    @Inject
    public PurgeScheduler(PurgeService purge) {
        this.purge = purge;
    }

    void onStart(@Observes @Initialized(ApplicationScoped.class) Object event) {
        long minutes = ConfigProvider.getConfig()
                .getOptionalValue("arago.purge.interval-minutes", Long.class).orElse(DEFAULT_INTERVAL_MINUTES);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "arago-purge-scheduler");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::safePurge, minutes, minutes, TimeUnit.MINUTES);
        LOG.log(System.Logger.Level.INFO, "RGPD purge scheduler started (every {0} min)", minutes);
    }

    @PreDestroy
    void onShutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * Runs one purge, never propagating: a task that throws from {@code scheduleAtFixedRate} silently
     * cancels all future runs, so the failure is caught and logged instead.
     */
    void safePurge() {
        try {
            PurgeService.PurgeResult result = purge.run();
            LOG.log(System.Logger.Level.DEBUG, "RGPD purge removed {0} ephemeral chat messages",
                    result.ephemeralChatPurged());
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "RGPD purge run failed; will retry next interval", e);
        }
    }
}
