package io.vidocq.tools.arago.ws;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class WsPingSchedulerTest {

    @Test
    void safePingSwallowsExceptions() {
        // A task that throws from scheduleAtFixedRate cancels all future runs — safePing must not propagate.
        RoomSocket throwing = new RoomSocket() {
            @Override
            void pingAll() {
                throw new RuntimeException("boom");
            }
        };
        assertDoesNotThrow(new WsPingScheduler(throwing)::safePing);
    }

    @Test
    void safePingInvokesThePing() {
        AtomicInteger calls = new AtomicInteger();
        RoomSocket counting = new RoomSocket() {
            @Override
            void pingAll() {
                calls.incrementAndGet();
            }
        };
        new WsPingScheduler(counting).safePing();
        assertEquals(1, calls.get());
    }
}
