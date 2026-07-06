package io.vidocq.tools.arago.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vidocq.chappe.api.WebSocket;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class RoomSocketPingTest {

    /** Minimal in-memory WebSocket double recording keepalive pings. */
    private static final class FakeSocket implements WebSocket {
        boolean open = true;
        boolean failPing;
        int pings;
        private final Map<String, Object> attrs = new ConcurrentHashMap<>();

        @Override public void sendText(String message) {}
        @Override public void sendBinary(ByteBuffer payload) {}
        @Override public void sendPing(ByteBuffer payload) throws IOException {
            if (failPing) {
                throw new IOException("broken pipe");
            }
            pings++;
        }
        @Override public void sendPong(ByteBuffer payload) {}
        @Override public void close(int code, String reason) { open = false; }
        @Override public boolean isOpen() { return open; }
        @Override public InetSocketAddress remoteAddress() { return null; }
        @Override public boolean isSecure() { return false; }
        @Override public String subprotocol() { return null; }
        @Override public Object attribute(String key) { return attrs.get(key); }
        @Override public WebSocket attribute(String key, Object value) { attrs.put(key, value); return this; }
    }

    private static Set<WebSocket> setOf(WebSocket... sockets) {
        Set<WebSocket> set = ConcurrentHashMap.newKeySet();
        for (WebSocket ws : sockets) {
            set.add(ws);
        }
        return set;
    }

    @Test
    void pingsEveryOpenSocket() {
        FakeSocket a = new FakeSocket();
        FakeSocket b = new FakeSocket();
        Set<WebSocket> peers = setOf(a, b);

        RoomSocket.pingPeers(peers);

        assertEquals(1, a.pings);
        assertEquals(1, b.pings);
        assertEquals(2, peers.size());
    }

    @Test
    void dropsAlreadyClosedSocketsWithoutPinging() {
        FakeSocket dead = new FakeSocket();
        dead.open = false;
        FakeSocket alive = new FakeSocket();
        Set<WebSocket> peers = setOf(dead, alive);

        RoomSocket.pingPeers(peers);

        assertEquals(0, dead.pings);
        assertFalse(peers.contains(dead));
        assertTrue(peers.contains(alive));
        assertEquals(1, alive.pings);
    }

    @Test
    void dropsSocketsFailingTheWrite() {
        FakeSocket broken = new FakeSocket();
        broken.failPing = true;
        FakeSocket alive = new FakeSocket();
        Set<WebSocket> peers = setOf(broken, alive);

        RoomSocket.pingPeers(peers);

        assertFalse(peers.contains(broken));
        assertTrue(peers.contains(alive));
        assertEquals(1, alive.pings);
    }
}
