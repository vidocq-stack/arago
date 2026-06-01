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

    private Playwright playwright;
    private Browser browser;
    private Page page;

    @When("I open the SPA at {string}")
    public void i_open_the_spa_at(String path) {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        page = browser.newPage();
        page.navigate(AragoApp.baseUrl() + path);
    }

    @Then("the page title contains {string}")
    public void the_page_title_contains(String expected) {
        assertTrue(page.title().contains(expected), () -> "title was: " + page.title());
    }

    @When("I fill {string} with {string}")
    public void i_fill_with(String testId, String value) {
        page.fill("[data-testid='" + testId + "']", value);
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
