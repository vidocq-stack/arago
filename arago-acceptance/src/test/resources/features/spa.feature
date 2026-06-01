@ui
Feature: Single-page app is served
  Chappe serves the Svelte build (arago-web) at the root.

  Scenario: The Svelte app loads at the root
    When I open the SPA at "/"
    Then the page title contains "Arago"

  Scenario: Superadmin logs into the admin console and invites a speaker
    When I open the SPA at "/admin.html"
    Then the page title contains "Admin"
    When I fill "login-username" with "root"
    And I fill "login-password" with "correct-horse-battery"
    And I click "login-submit"
    Then I see "Speakers"
    When I fill "new-email" with "frank.ui@oidc.test"
    And I click "new-speaker-submit"
    Then I see "frank.ui@oidc.test"
