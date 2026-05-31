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
