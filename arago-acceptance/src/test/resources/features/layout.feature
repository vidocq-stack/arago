Feature: LAB seating layout and seat locking
  A speaker configures a BLOCKS layout for a LAB room. Attendees lock a seat first-come-first-serve
  over the room WebSocket; a taken seat is broadcast to everyone and a losing claim is rejected.
  Leaving the room frees the seat, and a help request snapshots the seat's position (cf. arago-spec §4.5).

  Scenario: A speaker creates a LAB room with a seating layout
    Given I am logged in as superadmin
    When I POST "/api/admin/speakers" with body:
      """
      {"email":"heidi@oidc.test","displayName":"Heidi Speaker","role":"SPEAKER"}
      """
    Then the response status is 201
    Given a Keycloak token for user "heidi"
    When I POST "/api/rooms" with body:
      """
      {"title":"Workshop room","mode":"LAB","layout":{"rows":3,"blocks":[{"size":4,"label":"Left"},{"size":4,"label":"Right"}],"stagePos":"TOP","rowLabels":"NUMERIC","blockedSeats":[]}}
      """
    Then the response status is 201
    And I remember "roomId" from the JSON field "id"
    When I GET "/api/rooms/{roomId}"
    Then the response status is 200
    And the response body contains "Left"
    And the response body contains "Right"

  Scenario: A LAB room with no layout is rejected
    Given a Keycloak token for user "heidi"
    When I POST "/api/rooms" with body:
      """
      {"title":"Layoutless lab","mode":"LAB"}
      """
    Then the response status is 400

  Scenario: Two attendees compete for a seat; the loser is rejected, leaving frees the seat
    Given a Keycloak token for user "heidi"
    When I POST "/api/rooms" with body:
      """
      {"title":"Seating lab","mode":"LAB","layout":{"rows":2,"blocks":[{"size":3,"label":"Center"}],"stagePos":"TOP","rowLabels":"NUMERIC","blockedSeats":[]}}
      """
    Then the response status is 201
    And I remember "pin" from the JSON field "pin"
    And I remember "roomId" from the JSON field "id"
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
    Then WebSocket "A" receives a message containing "\"type\":\"layout\""
    When I open room WebSocket "B" with token "tokenB"
    When on WebSocket "A" I claim seat row 0 block 0 seat 1
    Then WebSocket "A" receives a message containing "\"action\":\"taken\""
    And WebSocket "B" receives a message containing "\"action\":\"taken\""
    When on WebSocket "B" I claim seat row 0 block 0 seat 1
    Then WebSocket "B" receives a message containing "\"reason\":\"seat-taken\""
    When on WebSocket "B" I claim seat row 0 block 0 seat 2
    Then WebSocket "B" receives a message containing "\"action\":\"taken\""
    When on WebSocket "A" I send the help request "lost"
    Then WebSocket "A" receives a message containing "Center"
    And WebSocket "A" receives a message containing "\"seat\":1"
    When I close WebSocket "A"
    Then WebSocket "B" receives a message containing "\"action\":\"free\""
    When on WebSocket "B" I claim seat row 0 block 0 seat 1
    Then WebSocket "B" receives a message containing "\"action\":\"taken\""
