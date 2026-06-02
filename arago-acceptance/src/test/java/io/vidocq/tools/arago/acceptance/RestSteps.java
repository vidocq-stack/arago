package io.vidocq.tools.arago.acceptance;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * REST step definitions: issue plain HTTP requests against the running Arago instance and assert on
 * status and JSON body. One instance per scenario (Cucumber), so {@link #response} is scenario-local.
 */
public class RestSteps {

    private final HttpClient http = HttpClient.newHttpClient();
    private HttpResponse<String> response;
    // Two independent auth tokens carried on distinct headers (see ARAGO-004 / AdminAuthenticator):
    private String adminToken; // superadmin HS256 token → X-Arago-Admin (ignored by cervantes/MP-JWT)
    private String bearer;     // Keycloak OIDC token → Authorization: Bearer (validated by cervantes)

    // Values captured from a response and substituted into later paths as {name} (e.g. a room id).
    // Shared with the other step classes via the injected World (see UiSteps for the UI side).
    private final java.util.Map<String, String> vars;

    public RestSteps(World world) {
        this.vars = world.vars;
    }

    // Room WebSocket (slice 4): the client connection + the messages it has received (thread-safe,
    // the JDK WebSocket delivers on its own thread). wsFrame reassembles fragmented text frames.
    private java.net.http.WebSocket ws;
    private final List<String> wsMessages = Collections.synchronizedList(new ArrayList<>());
    private final StringBuilder wsFrame = new StringBuilder();

    // A distinct client IP per scenario (new glue instance), sent as X-Forwarded-For — so the login
    // rate limiter buckets each scenario separately and one scenario's attempts don't throttle another.
    private static final java.util.concurrent.atomic.AtomicInteger SEQ = new java.util.concurrent.atomic.AtomicInteger();
    private final String clientIp = "10.0." + (SEQ.incrementAndGet() % 250) + ".7";

    @Given("I am logged in as superadmin")
    public void i_am_logged_in_as_superadmin() throws Exception {
        String body = "{\"username\":\"" + AragoApp.SUPERADMIN_USER
                + "\",\"password\":\"" + AragoApp.SUPERADMIN_PASSWORD + "\"}";
        HttpRequest req = request("/api/admin/login")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode(), () -> "login failed: " + r.body());
        try (JsonReader reader = Json.createReader(new StringReader(r.body()))) {
            adminToken = reader.readObject().getString("token");
        }
    }

    @Given("a Keycloak token for user {string}")
    public void a_keycloak_token_for_user(String username) {
        bearer = AragoApp.keycloakToken(username);
    }

    @When("I GET {string}")
    public void i_get(String path) throws Exception {
        response = http.send(authed(request(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Then("I remember {string} from the JSON field {string}")
    public void i_remember_from_json_field(String name, String field) {
        vars.put(name, json().getString(field));
    }

    @When("I POST {string}")
    public void i_post_no_body(String path) throws Exception {
        HttpRequest req = authed(request(path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        response = http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @When("I POST {string} with body:")
    public void i_post_with_body(String path, String body) throws Exception {
        HttpRequest req = authed(request(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(subst(body)))
                .build();
        response = http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @When("I POST {string} {int} times with body:")
    public void i_post_n_times_with_body(String path, int times, String body) throws Exception {
        for (int i = 0; i < times; i++) {
            i_post_with_body(path, body); // keeps the last response for the assertion
        }
    }

    @When("I PATCH {string} with body:")
    public void i_patch_with_body(String path, String body) throws Exception {
        HttpRequest req = authed(request(path))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(subst(body)))
                .build();
        response = http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @When("I PUT {string} with body:")
    public void i_put_with_body(String path, String body) throws Exception {
        HttpRequest req = authed(request(path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(subst(body)))
                .build();
        response = http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @When("I DELETE {string}")
    public void i_delete(String path) throws Exception {
        response = http.send(authed(request(path)).DELETE().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(AragoApp.baseUrl() + subst(path)))
                .timeout(Duration.ofSeconds(5))
                .header("X-Forwarded-For", clientIp);
    }

    /** Replaces {name} placeholders in a path with values captured via "I remember …". */
    private String subst(String path) {
        String result = path;
        for (java.util.Map.Entry<String, String> e : vars.entrySet()) {
            result = result.replace("{" + e.getKey() + "}", e.getValue());
        }
        return result;
    }

    private HttpRequest.Builder authed(HttpRequest.Builder b) {
        if (adminToken != null) {
            b = b.header("X-Arago-Admin", adminToken);
        }
        if (bearer != null) {
            b = b.header("Authorization", "Bearer " + bearer);
        }
        return b;
    }

    @Then("the response status is {int}")
    public void the_response_status_is(int expected) {
        assertEquals(expected, response.statusCode(), () -> "body: " + response.body());
    }

    @Then("the response header {string} contains {string}")
    public void the_response_header_contains(String name, String needle) {
        String value = response.headers().firstValue(name).orElse(null);
        assertTrue(value != null && value.contains(needle),
                () -> "expected header '" + name + "' to contain '" + needle + "' but was: " + value);
    }

    @Then("the JSON field {string} is {string}")
    public void the_json_field_is_string(String field, String expected) {
        assertEquals(expected, json().getString(field));
    }

    @Then("the JSON field {string} is {int}")
    public void the_json_field_is_int(String field, int expected) {
        assertEquals(expected, json().getInt(field));
    }

    @Then("the response body contains {string}")
    public void the_response_body_contains(String needle) {
        assertTrue(response.body().contains(needle),
                () -> "expected body to contain '" + needle + "' but was: " + response.body());
    }

    @Then("the response body does not contain {string}")
    public void the_response_body_does_not_contain(String needle) {
        assertTrue(!response.body().contains(needle),
                () -> "expected body NOT to contain '" + needle + "' but was: " + response.body());
    }

    @Then("the response body lists {string} before {string}")
    public void the_response_body_lists_before(String first, String second) {
        String body = response.body();
        int i = body.indexOf(first);
        int j = body.indexOf(second);
        assertTrue(i >= 0 && j >= 0 && i < j,
                () -> "expected '" + first + "' before '" + second + "' in: " + body);
    }

    @Then("the JSON field {string} is present")
    public void the_json_field_is_present(String field) {
        JsonObject o = json();
        assertTrue(o.containsKey(field) && !o.getString(field, "").isBlank(),
                () -> "missing/empty field '" + field + "' in: " + response.body());
    }

    private JsonObject json() {
        try (JsonReader r = Json.createReader(new StringReader(response.body()))) {
            return r.readObject();
        }
    }

    // --- Room WebSocket steps (slice 4) ---

    @When("I open a room WebSocket with the attendee token")
    public void i_open_a_room_websocket_with_the_attendee_token() throws Exception {
        String wsBase = AragoApp.baseUrl().replaceFirst("^http", "ws");
        URI uri = URI.create(wsBase + "/ws/rooms/" + vars.get("pin"));
        ws = HttpClient.newHttpClient().newWebSocketBuilder()
                .header("Authorization", "Bearer " + vars.get("attendeeToken"))
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(uri, new java.net.http.WebSocket.Listener() {
                    @Override
                    public void onOpen(java.net.http.WebSocket webSocket) {
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(java.net.http.WebSocket webSocket,
                                                     CharSequence data, boolean last) {
                        wsFrame.append(data);
                        if (last) {
                            wsMessages.add(wsFrame.toString());
                            wsFrame.setLength(0);
                        }
                        webSocket.request(1);
                        return null;
                    }
                })
                .get(10, TimeUnit.SECONDS);
    }

    @When("I send the chat message {string}")
    public void i_send_the_chat_message(String body) throws Exception {
        String frame = "{\"body\":\"" + body.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        ws.sendText(frame, true).get(5, TimeUnit.SECONDS);
    }

    @When("I send the persistent chat message {string}")
    public void i_send_the_persistent_chat_message(String body) throws Exception {
        String frame = "{\"body\":\"" + body.replace("\\", "\\\\").replace("\"", "\\\"")
                + "\",\"persistent\":true}";
        ws.sendText(frame, true).get(5, TimeUnit.SECONDS);
    }

    @When("I send the help request {string}")
    public void i_send_the_help_request(String message) throws Exception {
        String frame = "{\"type\":\"help\",\"message\":\""
                + message.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        ws.sendText(frame, true).get(5, TimeUnit.SECONDS);
    }

    @When("I cancel my help request")
    public void i_cancel_my_help_request() throws Exception {
        ws.sendText("{\"type\":\"help-cancel\"}", true).get(5, TimeUnit.SECONDS);
    }

    /**
     * Captures a field from the most recent {@code type:help} WebSocket frame (waits up to 5s for one)
     * — used to remember the server-assigned help id so a later REST claim/resolve can target it.
     */
    @Then("I remember {string} from the last help WebSocket message field {string}")
    public void i_remember_from_last_help_ws_message_field(String name, String field) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            String latest = null;
            synchronized (wsMessages) {
                for (String m : wsMessages) {
                    try (JsonReader r = Json.createReader(new StringReader(m))) {
                        JsonObject o = r.readObject();
                        if ("help".equals(o.getString("type", ""))) {
                            latest = o.getString(field, null);
                        }
                    } catch (RuntimeException ignore) {
                        // not a JSON frame — skip
                    }
                }
            }
            if (latest != null) {
                vars.put(name, latest);
                return;
            }
            Thread.sleep(50);
        }
        fail("no help WebSocket message carried field '" + field + "'; received: " + wsMessages);
    }

    @Then("the WebSocket receives a message containing {string}")
    public void the_websocket_receives_a_message_containing(String needle) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            synchronized (wsMessages) {
                for (String m : wsMessages) {
                    if (m.contains(needle)) {
                        return;
                    }
                }
            }
            Thread.sleep(50);
        }
        fail("WebSocket did not receive a message containing '" + needle + "'; received: " + wsMessages);
    }

    // --- Named room WebSockets (LAB layout, slice 5): several attendees connected at once. Each
    // connection keeps its own received-message buffer so per-attendee frames (e.g. a seat rejection,
    // sent only to the requester) can be asserted independently of the broadcast frames. ---

    private static final class WsConn {
        final java.net.http.WebSocket socket;
        final List<String> messages = Collections.synchronizedList(new ArrayList<>());
        final StringBuilder frame = new StringBuilder();
        volatile boolean closed;
        WsConn(java.net.http.WebSocket socket) { this.socket = socket; }
    }

    private final java.util.Map<String, WsConn> conns = new java.util.HashMap<>();

    @When("I open room WebSocket {string} with token {string}")
    public void i_open_named_room_websocket(String name, String tokenVar) throws Exception {
        String wsBase = AragoApp.baseUrl().replaceFirst("^http", "ws");
        URI uri = URI.create(wsBase + "/ws/rooms/" + vars.get("pin"));
        WsConn[] holder = new WsConn[1];
        java.net.http.WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .header("Authorization", "Bearer " + vars.get(tokenVar))
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(uri, new java.net.http.WebSocket.Listener() {
                    @Override
                    public void onOpen(java.net.http.WebSocket webSocket) {
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(java.net.http.WebSocket webSocket,
                                                     CharSequence data, boolean last) {
                        WsConn c = holder[0];
                        c.frame.append(data);
                        if (last) {
                            c.messages.add(c.frame.toString());
                            c.frame.setLength(0);
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(java.net.http.WebSocket webSocket, int statusCode, String reason) {
                        if (holder[0] != null) holder[0].closed = true;
                        return null;
                    }

                    @Override
                    public void onError(java.net.http.WebSocket webSocket, Throwable error) {
                        if (holder[0] != null) holder[0].closed = true;
                    }
                })
                .get(10, TimeUnit.SECONDS);
        WsConn conn = new WsConn(socket);
        holder[0] = conn;
        conns.put(name, conn);
    }

    @When("on WebSocket {string} I send the chat message {string}")
    public void on_ws_send_chat(String name, String body) throws Exception {
        String frame = "{\"body\":\"" + body.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        conns.get(name).socket.sendText(frame, true).get(5, TimeUnit.SECONDS);
    }

    @Then("WebSocket {string} is closed within {int} seconds")
    public void ws_is_closed_within(String name, int seconds) throws Exception {
        WsConn c = conns.get(name);
        long deadline = System.nanoTime() + Duration.ofSeconds(seconds).toNanos();
        while (System.nanoTime() < deadline) {
            if (c.closed) {
                return;
            }
            Thread.sleep(50);
        }
        fail("WebSocket '" + name + "' was not closed within " + seconds + "s");
    }

    @When("on WebSocket {string} I claim seat row {int} block {int} seat {int}")
    public void on_ws_i_claim_seat(String name, int row, int block, int seat) throws Exception {
        String frame = "{\"type\":\"seat\",\"row\":" + row + ",\"block\":" + block + ",\"seat\":" + seat + "}";
        conns.get(name).socket.sendText(frame, true).get(5, TimeUnit.SECONDS);
    }

    @When("on WebSocket {string} I release my seat")
    public void on_ws_i_release_my_seat(String name) throws Exception {
        conns.get(name).socket.sendText("{\"type\":\"seat-release\"}", true).get(5, TimeUnit.SECONDS);
    }

    @When("on WebSocket {string} I send the help request {string}")
    public void on_ws_i_send_help(String name, String message) throws Exception {
        String frame = "{\"type\":\"help\",\"message\":\""
                + message.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        conns.get(name).socket.sendText(frame, true).get(5, TimeUnit.SECONDS);
    }

    @When("I close WebSocket {string}")
    public void i_close_named_ws(String name) {
        WsConn c = conns.get(name);
        if (c != null) {
            c.socket.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "scenario step");
        }
    }

    @Then("WebSocket {string} receives a message containing {string}")
    public void named_ws_receives_message_containing(String name, String needle) throws Exception {
        WsConn c = conns.get(name);
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            synchronized (c.messages) {
                for (String m : c.messages) {
                    if (m.contains(needle)) {
                        return;
                    }
                }
            }
            Thread.sleep(50);
        }
        fail("WebSocket '" + name + "' did not receive a message containing '" + needle
                + "'; received: " + c.messages);
    }

    @Then("WebSocket {string} does not receive a message containing {string}")
    public void named_ws_does_not_receive(String name, String needle) throws Exception {
        WsConn c = conns.get(name);
        // Give any in-flight broadcast a moment to arrive before asserting its absence.
        Thread.sleep(500);
        synchronized (c.messages) {
            for (String m : c.messages) {
                assertTrue(!m.contains(needle),
                        () -> "WebSocket '" + name + "' unexpectedly received '" + needle + "': " + c.messages);
            }
        }
    }

    @After
    public void closeWebSocket() {
        if (ws != null) {
            ws.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "scenario end");
        }
        for (WsConn c : conns.values()) {
            c.socket.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "scenario end");
        }
    }
}
