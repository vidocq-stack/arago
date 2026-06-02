package io.vidocq.tools.arago.pins;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;

class SsrfGuardTest {

    private static boolean blocked(String ip) throws Exception {
        // Literal IPs do not trigger DNS resolution.
        return SsrfGuard.isBlocked(InetAddress.getByName(ip));
    }

    @Test
    void blocksInternalAndSpecialUseAddresses() throws Exception {
        for (String ip : new String[] {
                "127.0.0.1",     // loopback
                "0.0.0.0",       // any-local
                "10.0.0.1",      // private (site-local)
                "172.16.5.5",    // private
                "192.168.1.1",   // private
                "169.254.0.1",   // link-local
                "100.64.0.1",    // CGNAT (not flagged by InetAddress)
                "224.0.0.1",     // multicast
                "::1",           // IPv6 loopback
                "fe80::1",       // IPv6 link-local
                "fc00::1",       // IPv6 unique-local (ULA)
                "::ffff:127.0.0.1" // IPv4-mapped loopback
        }) {
            assertTrue(blocked(ip), () -> "expected blocked: " + ip);
        }
    }

    @Test
    void allowsPublicAddresses() throws Exception {
        assertFalse(blocked("8.8.8.8"));
        assertFalse(blocked("1.1.1.1"));
        assertFalse(blocked("2606:4700:4700::1111")); // Cloudflare IPv6
    }
}
