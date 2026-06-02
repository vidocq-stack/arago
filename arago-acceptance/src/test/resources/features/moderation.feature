Feature: Room moderation — mute / kick attendee (Phase 2)
  The owner-speaker can mute an attendee (their messages stop being broadcast) and kick them (their
  socket is closed and they cannot rejoin), by pseudo (cf. arago-spec §7). Moderation is owner-only.

  Scenario: Mute drops an attendee's messages; unmute restores them; kick closes their socket
    Given I am logged in as superadmin
    When I POST "/api/admin/speakers" with body:
      """
      {"email":"mona@oidc.test","displayName":"Mona Speaker","role":"SPEAKER"}
      """
    Then the response status is 201
    Given a Keycloak token for user "mona"
    When I POST "/api/rooms" with body:
      """
      {"title":"Moderated room","mode":"CONF"}
      """
    Then the response status is 201
    And I remember "roomId" from the JSON field "id"
    And I remember "pin" from the JSON field "pin"
    When I POST "/api/rooms/join" with body:
      """
      {"pin":"{pin}","pseudo":"Alice"}
      """
    Then the response status is 200
    And I remember "tokenA" from the JSON field "token"
    When I POST "/api/rooms/join" with body:
      """
      {"pin":"{pin}","pseudo":"Bob"}
      """
    Then the response status is 200
    And I remember "tokenB" from the JSON field "token"
    When I open room WebSocket "A" with token "tokenA"
    And I open room WebSocket "B" with token "tokenB"
    # Mute Alice → her message is not broadcast to Bob.
    When I POST "/api/rooms/{roomId}/moderation/mute" with body:
      """
      {"pseudo":"Alice"}
      """
    Then the response status is 200
    When on WebSocket "A" I send the chat message "blocked while muted"
    Then WebSocket "B" does not receive a message containing "blocked while muted"
    # Unmute Alice → her message is broadcast again.
    When I POST "/api/rooms/{roomId}/moderation/unmute" with body:
      """
      {"pseudo":"Alice"}
      """
    Then the response status is 200
    When on WebSocket "A" I send the chat message "allowed after unmute"
    Then WebSocket "B" receives a message containing "allowed after unmute"
    # Kick Alice → her socket is closed.
    When I POST "/api/rooms/{roomId}/moderation/kick" with body:
      """
      {"pseudo":"Alice"}
      """
    Then the response status is 200
    And WebSocket "A" is closed within 3 seconds

  Scenario: Moderation requires a speaker token
    When I POST "/api/rooms/any/moderation/kick" with body:
      """
      {"pseudo":"Alice"}
      """
    Then the response status is 401
