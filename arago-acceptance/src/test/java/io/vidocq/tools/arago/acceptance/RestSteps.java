package io.vidocq.tools.arago.acceptance;

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

/**
 * REST step definitions: issue plain HTTP requests against the running Arago instance and assert on
 * status and JSON body. One instance per scenario (Cucumber), so {@link #response} is scenario-local.
 */
public class RestSteps {

    private final HttpClient http = HttpClient.newHttpClient();
    private HttpResponse<String> response;

    @When("I GET {string}")
    public void i_get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(AragoApp.baseUrl() + path))
                .timeout(Duration.ofSeconds(5)).GET().build();
        response = http.send(req, HttpResponse.BodyHandlers.ofString());
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

    private JsonObject json() {
        try (JsonReader r = Json.createReader(new StringReader(response.body()))) {
            return r.readObject();
        }
    }
}
