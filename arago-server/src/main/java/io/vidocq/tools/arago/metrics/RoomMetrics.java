package io.vidocq.tools.arago.metrics;

import io.vidocq.tools.arago.persistence.RoomRepository;
import io.vidocq.tools.arago.persistence.RoomStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Gauge;

/**
 * Application metrics for Arago (cf. arago-spec §5/§12). The {@code @Gauge} is discovered by dirac
 * (MP Metrics) at startup and evaluated on each {@code /metrics} scrape — closing the §12 criterion
 * "{@code /metrics} exposes the active-rooms count".
 */
@ApplicationScoped
public class RoomMetrics {

    @Inject
    RoomRepository rooms;

    /** Number of rooms currently {@code ACTIVE} (joinable). Exposed at {@code /metrics}. */
    @Gauge(name = "arago_active_rooms", unit = MetricUnits.NONE,
            description = "Number of rooms currently ACTIVE (joinable).")
    public long activeRooms() {
        return rooms.countByStatus(RoomStatus.ACTIVE);
    }
}
