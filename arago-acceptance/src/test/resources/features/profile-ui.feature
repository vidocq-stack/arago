@ui
Feature: Attendee "my data" page (RGPD magic-link landing)
  An attendee follows their magic link to the "Mes données" page (served at the clean URL /mes-donnees),
  sees their profile + persistent questions, and can erase everything from there (cf. arago-spec §4.7).
  Drives the real served Svelte page with Playwright.

  Scenario: Attendee opens the magic link, sees their data, and erases it
    Given I am logged in as superadmin
    When I POST "/api/admin/speakers" with body:
      """
      {"email":"karl@oidc.test","displayName":"Karl Speaker","role":"SPEAKER"}
      """
    Then the response status is 201
    Given a Keycloak token for user "karl"
    When I POST "/api/rooms" with body:
      """
      {"title":"RGPD UI room","mode":"CONF"}
      """
    Then the response status is 201
    And I remember "pin" from the JSON field "pin"
    When I POST "/api/rooms/join" with body:
      """
      {"pin":"{pin}","pseudo":"Niaj","email":"attendee.ui.rgpd@example.test","consentAccepted":true,"consentTextVersion":"v1"}
      """
    Then the response status is 200
    And I remember "attendeeToken" from the JSON field "token"
    When I open a room WebSocket with the attendee token
    And I send the persistent chat message "garder cette question UI"
    Then the WebSocket receives a message containing "garder cette question UI"
    When I POST "/api/profile/magic-link" with body:
      """
      {"email":"attendee.ui.rgpd@example.test"}
      """
    Then the response status is 202
    And I remember "magicToken" from the JSON field "token"
    When I open the SPA at "/mes-donnees?token={magicToken}"
    Then the page title contains "Mes données"
    And I see "attendee.ui.rgpd@example.test"
    And I see "garder cette question UI"
    When I click "delete-all"
    And I click "delete-confirm"
    Then I see "supprimées"
