@ui
Feature: Reveal deck plugin (Phase 4)
  The Arago reveal plugin, loaded in a deck opened with ?aragoRoom&aragoSecret, connects to the room
  WebSocket and executes commands the speaker sends. Driven against the bundled demo deck (no network
  reveal.js); the speaker's command arrives over REST and advances the slide (cf. arago-spec §4.6).

  Scenario: A speaker command advances the reveal deck
    Given I am logged in as superadmin
    When I POST "/api/admin/speakers" with body:
      """
      {"email":"rita@oidc.test","displayName":"Rita Speaker","role":"SPEAKER"}
      """
    Then the response status is 201 or 409
    Given a Keycloak token for user "rita"
    When I POST "/api/rooms" with body:
      """
      {"title":"Reveal demo talk","mode":"CONF"}
      """
    Then the response status is 201
    And I remember "roomId" from the JSON field "id"
    And I remember "pin" from the JSON field "pin"
    When I POST "/api/rooms/{roomId}/reveal/enable"
    Then the response status is 200
    And I remember "secret" from the JSON field "secret"
    When I open the SPA at "/reveal-demo?aragoRoom={pin}&aragoSecret={secret}"
    Then I see "connected"
    When I POST "/api/rooms/{roomId}/reveal/cmd" with body:
      """
      {"cmd":"next"}
      """
    Then the response status is 200
    And I see "1"
