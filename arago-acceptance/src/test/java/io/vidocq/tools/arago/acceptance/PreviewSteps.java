package io.vidocq.tools.arago.acceptance;

import com.sun.net.httpserver.HttpServer;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Spins up a tiny local HTTP server (JDK {@code com.sun.net.httpserver}) on {@code 127.0.0.1} serving a
 * page with OpenGraph metadata, so the URL-pin preview fetcher can be exercised end-to-end against a
 * real socket. Loopback is normally blocked by the SSRF guard — {@code AragoApp} sets
 * {@code arago.pins.preview.allow-private=true} for tests. The server URL is shared via {@link World}.
 */
public class PreviewSteps {

    private final World world;
    private HttpServer server;

    public PreviewSteps(World world) {
        this.world = world;
    }

    @Given("a local OpenGraph page titled {string}")
    public void a_local_open_graph_page_titled(String title) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String html = "<html><head>"
                + "<meta property=\"og:title\" content=\"" + title + "\">"
                + "<meta property=\"og:description\" content=\"a demo page\">"
                + "<meta property=\"og:image\" content=\"http://127.0.0.1/img.png\">"
                + "</head><body>hello</body></html>";
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        world.vars.put("previewUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @After
    public void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }
}
