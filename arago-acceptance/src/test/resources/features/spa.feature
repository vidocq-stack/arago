@ui
Feature: Single-page app is served
  Chappe serves the Svelte build (arago-web) at the root.

  Scenario: The Svelte app loads at the root
    When I open the SPA at "/"
    Then the page title contains "Arago"
