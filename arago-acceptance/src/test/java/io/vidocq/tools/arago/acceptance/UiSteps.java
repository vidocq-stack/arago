package io.vidocq.tools.arago.acceptance;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import io.cucumber.java.After;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI step definitions: drive the served Svelte SPA with a headless Chromium (Playwright). Tagged
 * {@code @ui} in the features, so a run without browsers can skip them
 * ({@code -Dcucumber.filter.tags='not @ui'}). Playwright downloads Chromium on first launch.
 */
public class UiSteps {

    private final World world;
    private Playwright playwright;
    private Browser browser;
    private Page page;

    public UiSteps(World world) {
        this.world = world;
    }

    @When("I open the SPA at {string}")
    public void i_open_the_spa_at(String path) {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        page = browser.newPage();
        page.navigate(AragoApp.baseUrl() + path);
    }

    /** Fills an input with a value captured earlier (e.g. a room PIN created over REST). */
    @When("I fill {string} with remembered {string}")
    public void i_fill_with_remembered(String testId, String name) {
        page.fill("[data-testid='" + testId + "']", world.vars.get(name));
    }

    /** Clicks a seat in the top-down view by its 0-indexed coordinate (cf. arago-spec §4.5). */
    @When("I click seat {int} {int} {int}")
    public void i_click_seat(int row, int block, int seat) {
        page.click("[data-testid='seat-" + row + "-" + block + "-" + seat + "']");
    }

    @Then("the page title contains {string}")
    public void the_page_title_contains(String expected) {
        assertTrue(page.title().contains(expected), () -> "title was: " + page.title());
    }

    @When("I fill {string} with {string}")
    public void i_fill_with(String testId, String value) {
        page.fill("[data-testid='" + testId + "']", value);
    }

    /**
     * Submits the Keycloak login form (default login theme: {@code #username}, {@code #password},
     * {@code #kc-login}). Used by the OIDC front-channel scenario, which lands on Keycloak after the
     * SPA's "Se connecter (Keycloak)" button triggers {@code GET /api/oidc/login} → 302.
     */
    @When("I log in to Keycloak as {string} with password {string}")
    public void i_log_in_to_keycloak(String username, String password) {
        page.waitForSelector("#username");
        page.fill("#username", username);
        page.fill("#password", password);
        page.click("#kc-login");
    }

    @When("I click {string}")
    public void i_click(String testId) {
        page.click("[data-testid='" + testId + "']");
    }

    @Then("I see {string}")
    public void i_see(String text) {
        // Playwright auto-waits for the element to appear (handles the async fetch/render).
        page.getByText(text, new Page.GetByTextOptions().setExact(false)).first()
                .waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(5000));
    }

    @After("@ui")
    public void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }
}
