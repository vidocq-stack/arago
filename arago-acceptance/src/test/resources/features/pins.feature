Feature: Pinned content (speaker)
  Speakers pin TEXT/URL/CODE/SECRET blocks in a room; pins are broadcast live to attendees over the
  room WebSocket and replayed on join. SECRET pins are purged when the room closes (cf. arago-spec §4.4).

  Scenario: A speaker pins a code block and the attendee receives it over the WebSocket
    Given I am logged in as superadmin
    When I POST "/api/admin/speakers" with body:
      """
      {"email":"erin@oidc.test","displayName":"Erin Speaker","role":"SPEAKER"}
      """
    Then the response status is 201
    Given a Keycloak token for user "erin"
    When I POST "/api/rooms" with body:
      """
      {"title":"Pinned room","mode":"CONF"}
      """
    Then the response status is 201
    And I remember "pin" from the JSON field "pin"
    And I remember "roomId" from the JSON field "id"
    When I POST "/api/rooms/join" with body:
      """
      {"pin":"{pin}","pseudo":"Zoe"}
      """
    Then the response status is 200
    And I remember "attendeeToken" from the JSON field "token"
    When I open a room WebSocket with the attendee token
    When I POST "/api/rooms/{roomId}/pins" with body:
      """
      {"type":"CODE","content":"print('hi')","lang":"python"}
      """
    Then the response status is 201
    And the JSON field "type" is "CODE"
    And the WebSocket receives a message containing "print('hi')"

  Scenario: Pins are listed and a SECRET pin is purged when the room ends
    Given a Keycloak token for user "erin"
    When I POST "/api/rooms" with body:
      """
      {"title":"Secret room","mode":"CONF"}
      """
    Then the response status is 201
    And I remember "roomId" from the JSON field "id"
    When I POST "/api/rooms/{roomId}/pins" with body:
      """
      {"type":"TEXT","content":"keep this note"}
      """
    Then the response status is 201
    And I remember "textPinId" from the JSON field "id"
    When I POST "/api/rooms/{roomId}/pins" with body:
      """
      {"type":"SECRET","content":"sk-demo-12345"}
      """
    Then the response status is 201
    When I GET "/api/rooms/{roomId}/pins"
    Then the response status is 200
    And the response body contains "sk-demo-12345"
    When I DELETE "/api/pins/{textPinId}"
    Then the response status is 204
    When I POST "/api/rooms/{roomId}/end"
    Then the response status is 200
    When I GET "/api/rooms/{roomId}/pins"
    Then the response status is 200
    And the response body does not contain "sk-demo-12345"
