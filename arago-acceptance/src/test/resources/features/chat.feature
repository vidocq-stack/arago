Feature: Room chat over WebSocket
  An attendee joins an active room (HTTP), then connects the room WebSocket with their attendee
  token and exchanges chat messages. This exercises the declarative WebSocket mount (runtime socle),
  the handshake auth, message persistence and broadcast (cf. arago-spec §4.3/§4.6).

  Scenario: An attendee posts a chat message and receives the broadcast
    Given I am logged in as superadmin
    When I POST "/api/admin/speakers" with body:
      """
      {"email":"dave@oidc.test","displayName":"Dave Speaker","role":"SPEAKER"}
      """
    Then the response status is 201
    Given a Keycloak token for user "dave"
    When I POST "/api/rooms" with body:
      """
      {"title":"Chat room","mode":"CONF"}
      """
    Then the response status is 201
    And I remember "pin" from the JSON field "pin"
    When I POST "/api/rooms/join" with body:
      """
      {"pin":"{pin}","pseudo":"Zoe"}
      """
    Then the response status is 200
    And I remember "attendeeToken" from the JSON field "token"
    When I open a room WebSocket with the attendee token
    And I send the chat message "hello room"
    Then the WebSocket receives a message containing "hello room"
