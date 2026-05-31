package io.vidocq.tools.arago.admin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit coverage of {@link AdminAuditService#truncateIp} (cf. arago-spec §10.2 — never store a full IP).
 */
class AdminAuditServiceTest {

    @Test
    void truncates_ipv4_to_slash_24() {
        assertEquals("203.0.113.0/24", AdminAuditService.truncateIp("203.0.113.42"));
    }

    @Test
    void uses_first_hop_of_forwarded_for() {
        assertEquals("198.51.100.0/24", AdminAuditService.truncateIp("198.51.100.7, 10.0.0.1, 10.0.0.2"));
    }

    @Test
    void truncates_ipv6_to_slash_48() {
        assertEquals("2001:db8:1234::/48", AdminAuditService.truncateIp("2001:db8:1234:5678::1"));
    }

    @Test
    void returns_null_for_missing_or_unparseable() {
        assertNull(AdminAuditService.truncateIp(null));
        assertNull(AdminAuditService.truncateIp(""));
        assertNull(AdminAuditService.truncateIp("not-an-ip"));
    }
}
