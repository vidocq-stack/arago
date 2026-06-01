package io.vidocq.tools.arago.acceptance;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private final java.util.Map<String, String> vars = new java.util.HashMap<>();

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
                .POST(HttpRequest.BodyPublishers.ofString(body))
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
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
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
}
