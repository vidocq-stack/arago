@ui
Feature: Preferences — language (FR/EN) and theme (light/dark) (Phase 6 polish)
  The public attendee page is fully bilingual and offers a light/dark theme. Choices are explicit
  (no reliance on the browser default, which varies in CI) and reflected on the <html> element for a11y.

  Scenario: Switching language re-labels the join page
    When I open the SPA at "/"
    And I click "lang-en"
    Then the html "lang" attribute is "en"
    And I see "Join"
    When I click "lang-fr"
    Then the html "lang" attribute is "fr"
    And I see "Rejoindre"

  Scenario: Toggling the theme flips the document theme
    When I open the SPA at "/"
    Then the html "data-theme" attribute is "light"
    When I click "theme-toggle"
    Then the html "data-theme" attribute is "dark"
    When I click "theme-toggle"
    Then the html "data-theme" attribute is "light"
