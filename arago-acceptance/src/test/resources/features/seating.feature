@ui
Feature: LAB seating — attendee top-down view
  An attendee joins a LAB room by PIN, sees the top-down seating plan rendered from the layout pushed
  over the WebSocket, and taps a free seat to sit (server-authoritative). The room is created over
  REST by a speaker; the PIN is shared with the browser via the per-scenario World (cf. arago-spec §4.5).

  Scenario: An attendee joins a LAB room and takes a seat in the top-down view
    Given I am logged in as superadmin
    When I POST "/api/admin/speakers" with body:
      """
      {"email":"ivan@oidc.test","displayName":"Ivan Speaker","role":"SPEAKER"}
      """
    Then the response status is 201
    Given a Keycloak token for user "ivan"
    When I POST "/api/rooms" with body:
      """
      {"title":"Top-down lab","mode":"LAB","layout":{"rows":2,"blocks":[{"size":3,"label":"Center"}],"stagePos":"TOP","rowLabels":"NUMERIC","blockedSeats":[]}}
      """
    Then the response status is 201
    And I remember "pin" from the JSON field "pin"
    When I open the SPA at "/"
    And I fill "join-pin" with remembered "pin"
    And I fill "join-pseudo" with "Mia"
    And I click "join-submit"
    Then I see "Choisissez une place"
    When I click seat 0 0 1
    Then I see "Place : R1"
