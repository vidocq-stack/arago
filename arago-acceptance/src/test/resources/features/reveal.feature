Feature: Reveal remote control protocol (Phase 4)
  A speaker enables the reveal session; the deck plugin connects with the secret and exchanges slide
  state/commands over the room WebSocket. Commands are owner-only REST; state flows over the WS and is
  broadcast to followers (cf. arago-spec §4.6).

  Scenario: Enable the session, send a command to the deck, and the viewer follows the slide state
    Given I am logged in as superadmin
    When I POST "/api/admin/speakers" with body:
      """
      {"email":"rita@oidc.test","displayName":"Rita Speaker","role":"SPEAKER"}
      """
    Then the response status is 201 or 409
    Given a Keycloak token for user "rita"
    When I POST "/api/rooms" with body:
      """
      {"title":"Reveal talk","mode":"CONF"}
      """
    Then the response status is 201
    And I remember "roomId" from the JSON field "id"
    And I remember "pin" from the JSON field "pin"
    When I POST "/api/rooms/{roomId}/reveal/enable"
    Then the response status is 200
    And the JSON field "pin" is present
    And I remember "secret" from the JSON field "secret"
    When I POST "/api/rooms/join" with body:
      """
      {"pin":"{pin}","pseudo":"Viewer"}
      """
    Then the response status is 200
    And I remember "attendeeToken" from the JSON field "token"
    When I open room WebSocket "deck" with reveal secret "secret"
    And I open room WebSocket "viewer" with token "attendeeToken"
    When I POST "/api/rooms/{roomId}/reveal/cmd" with body:
      """
      {"cmd":"next"}
      """
    Then the response status is 200
    And WebSocket "deck" receives a message containing "reveal.cmd"
    When on WebSocket "deck" I report slide 2
    Then WebSocket "viewer" receives a message containing "\"type\":\"reveal.state\""
    And WebSocket "viewer" receives a message containing "\"indexh\":2"

  Scenario: A reveal command requires a speaker token
    When I POST "/api/rooms/any-room/reveal/cmd" with body:
      """
      {"cmd":"next"}
      """
    Then the response status is 401
