package io.vidocq.tools.arago.admin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginRateLimiterTest {

    @Test
    void caps_attempts_per_window() {
        LoginRateLimiter rl = new LoginRateLimiter(3, 60_000_000_000L);
        assertTrue(rl.allow("ip1"));
        assertTrue(rl.allow("ip1"));
        assertTrue(rl.allow("ip1"));
        assertFalse(rl.allow("ip1"), "4th attempt exceeds the cap of 3");
    }

    @Test
    void keys_are_independent() {
        LoginRateLimiter rl = new LoginRateLimiter(1, 60_000_000_000L);
        assertTrue(rl.allow("a"));
        assertFalse(rl.allow("a"));
        assertTrue(rl.allow("b"), "a different key has its own budget");
    }

    @Test
    void window_resets_after_expiry() {
        LoginRateLimiter rl = new LoginRateLimiter(1, 1L); // 1ns window
        assertTrue(rl.allow("a"));
        long t = System.nanoTime();
        while (System.nanoTime() - t < 1_000) {
            // spin a hair so nanoTime advances past the 1ns window
        }
        assertTrue(rl.allow("a"), "a fresh window must allow again");
    }
}
