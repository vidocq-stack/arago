Feature: Past-event history & exports (Phase 5)
  After an event, the owner can review the chat history + help requests + stats, and export the chat as
  Markdown and the help requests as CSV (cf. arago-spec §11 Phase 5). Owner-only.

  Scenario: Chat history, stats and exports for a room
    Given I am logged in as superadmin
    When I POST "/api/admin/speakers" with body:
      """
      {"email":"sam@oidc.test","displayName":"Sam Speaker","role":"SPEAKER"}
      """
    Then the response status is 201
    Given a Keycloak token for user "sam"
    When I POST "/api/rooms" with body:
      """
      {"title":"Recap talk","mode":"CONF"}
      """
    Then the response status is 201
    And I remember "roomId" from the JSON field "id"
    And I remember "pin" from the JSON field "pin"
    When I POST "/api/rooms/join" with body:
      """
      {"pin":"{pin}","pseudo":"Wendy","email":"wendy@example.test","consentAccepted":true,"consentTextVersion":"v1"}
      """
    Then the response status is 200
    And I remember "attendeeToken" from the JSON field "token"
    When I open a room WebSocket with the attendee token
    And I send the persistent chat message "remember this question"
    Then the WebSocket receives a message containing "remember this question"
    When I send the help request "need a hand"
    Then the WebSocket receives a message containing "PENDING"
    And I remember "helpId" from the last help WebSocket message field "id"
    When I POST "/api/rooms/{roomId}/help/{helpId}/claim"
    Then the response status is 200
    When I POST "/api/rooms/{roomId}/help/{helpId}/resolve"
    Then the response status is 200
    # History
    When I GET "/api/rooms/{roomId}/chat"
    Then the response status is 200
    And the response body contains "remember this question"
    When I GET "/api/rooms/{roomId}/stats"
    Then the response status is 200
    And the JSON field "messages" is 1
    And the JSON field "helpResolved" is 1
    # Exports
    When I GET "/api/rooms/{roomId}/chat/export.md"
    Then the response status is 200
    And the response body contains "# Recap talk"
    And the response body contains "remember this question"
    When I GET "/api/rooms/{roomId}/help/export.csv"
    Then the response status is 200
    And the response body contains "id,attendee,position,status"
    And the response body contains "RESOLVED"

  Scenario: Stats require a speaker token
    When I GET "/api/rooms/any-room/stats"
    Then the response status is 401
