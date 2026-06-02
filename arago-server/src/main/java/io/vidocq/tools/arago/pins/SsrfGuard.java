package io.vidocq.tools.arago.pins;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * SSRF guard for the OpenGraph URL-preview fetcher (§4.4/§11 Phase 2). Server-side fetches of
 * attendee/speaker-supplied URLs must never reach internal targets, so an address is <em>blocked</em>
 * when it is loopback, any-local, link-local, site-local or multicast (via {@link InetAddress} flags),
 * plus — not covered by those flags — the IPv4 CGNAT range {@code 100.64.0.0/10} and the IPv6 ULA range
 * {@code fc00::/7}. IPv4-mapped IPv6 addresses ({@code ::ffff:a.b.c.d}) are unwrapped and re-checked.
 *
 * <p>Pure and unit-tested. {@link #hostIsBlocked(String)} resolves a host and blocks if <em>any</em>
 * resolved address is blocked (treating resolution failure as blocked).</p>
 */
public final class SsrfGuard {

    private SsrfGuard() {}

    /** True if the address must not be contacted (internal / special-use). */
    public static boolean isBlocked(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress() || addr.isMulticastAddress()) {
            return true;
        }
        byte[] a = addr.getAddress();
        if (a.length == 4) {
            return isBlockedV4(a);
        }
        if (a.length == 16) {
            // IPv4-mapped IPv6 (::ffff:a.b.c.d) — unwrap and re-check as IPv4.
            boolean prefixZero = true;
            for (int i = 0; i < 10; i++) {
                if (a[i] != 0) {
                    prefixZero = false;
                    break;
                }
            }
            if (prefixZero && (a[10] & 0xff) == 0xff && (a[11] & 0xff) == 0xff) {
                return isBlockedV4(new byte[] {a[12], a[13], a[14], a[15]});
            }
            // fc00::/7 — IPv6 unique local addresses (isSiteLocalAddress only covers deprecated fec0::/10).
            if ((a[0] & 0xfe) == 0xfc) {
                return true;
            }
            return false;
        }
        return true; // unknown address family — block defensively
    }

    private static boolean isBlockedV4(byte[] a) {
        int b0 = a[0] & 0xff;
        int b1 = a[1] & 0xff;
        // 100.64.0.0/10 — carrier-grade NAT (RFC 6598), not flagged by InetAddress.
        return b0 == 100 && (b1 & 0xC0) == 0x40;
    }

    /** Resolves {@code host} and returns true if it is unresolvable or any resolved address is blocked. */
    public static boolean hostIsBlocked(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }
        try {
            InetAddress[] addrs = InetAddress.getAllByName(host);
            if (addrs.length == 0) {
                return true;
            }
            for (InetAddress addr : addrs) {
                if (isBlocked(addr)) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException e) {
            return true;
        }
    }
}
