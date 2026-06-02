@ui
Feature: Speaker console (Phase 3)
  A speaker logs into the console at /speaker (OIDC), manages their rooms, and runs a LAB room: the live
  help queue lets them resolve a request (cf. arago-spec §9). Drives the served Svelte console with
  Playwright; the attendee side is driven over REST/WebSocket.

  Scenario: A speaker logs into the console and creates a room
    Given I am logged in as superadmin
    When I POST "/api/admin/speakers" with body:
      """
      {"email":"owen@oidc.test","displayName":"Owen Speaker","role":"SPEAKER"}
      """
    Then the response status is 201
    When I open the SPA at "/speaker"
    And I click "speaker-login"
    And I log in to Keycloak as "owen" with password "pw"
    Then I see "owen@oidc.test"
    When I fill "room-title" with "Ma conf console"
    And I click "create-room"
    Then I see "Ma conf console"

  Scenario: A speaker resolves a help request from the console
    Given a Keycloak token for user "owen"
    When I POST "/api/rooms" with body:
      """
      {"title":"Console lab","mode":"LAB","layout":{"rows":2,"blocks":[{"size":4,"label":"Center"}],"stagePos":"TOP","rowLabels":"NUMERIC","blockedSeats":[]}}
      """
    Then the response status is 201
    And I remember "pin" from the JSON field "pin"
    And I remember "roomId" from the JSON field "id"
    When I POST "/api/rooms/join" with body:
      """
      {"pin":"{pin}","pseudo":"Quentin"}
      """
    Then the response status is 200
    And I remember "attendeeToken" from the JSON field "token"
    When I open a room WebSocket with the attendee token
    And I send the help request "console help please"
    Then the WebSocket receives a message containing "console help please"
    When I open the SPA at "/speaker"
    And I click "speaker-login"
    And I log in to Keycloak as "owen" with password "pw"
    Then I see "owen@oidc.test"
    When I click "open-room"
    Then I see "console help please"
    When I click "help-resolve"
    Then I see "résolue"
