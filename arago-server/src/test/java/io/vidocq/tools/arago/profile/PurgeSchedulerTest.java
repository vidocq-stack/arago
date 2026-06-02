package io.vidocq.tools.arago.profile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class PurgeSchedulerTest {

    @Test
    void safePurgeSwallowsExceptions() {
        // A task that throws from scheduleAtFixedRate cancels all future runs — safePurge must not propagate.
        PurgeService throwing = new PurgeService() {
            @Override
            public PurgeResult run() {
                throw new RuntimeException("boom");
            }
        };
        assertDoesNotThrow(new PurgeScheduler(throwing)::safePurge);
    }

    @Test
    void safePurgeInvokesThePurge() {
        AtomicInteger calls = new AtomicInteger();
        PurgeService counting = new PurgeService() {
            @Override
            public PurgeResult run() {
                calls.incrementAndGet();
                return new PurgeResult(0);
            }
        };
        new PurgeScheduler(counting).safePurge();
        assertEquals(1, calls.get());
    }
}
