Feature: LAB help requests
  In a LAB room an attendee raises a "need help" request over the room WebSocket; the speaker sees it
  in the LAB panel, claims it, then resolves it. Each state change is broadcast back to the room and
  active requests are replayed on join. One active request per attendee at a time (cf. arago-spec §4.5).

  Scenario: An attendee raises help, the speaker claims and resolves it
    Given I am logged in as superadmin
    When I POST "/api/admin/speakers" with body:
      """
      {"email":"grace@oidc.test","displayName":"Grace Speaker","role":"SPEAKER"}
      """
    Then the response status is 201
    Given a Keycloak token for user "grace"
    When I POST "/api/rooms" with body:
      """
      {"title":"Hands-on lab","mode":"LAB","layout":{"rows":2,"blocks":[{"size":4,"label":"Center"}],"stagePos":"TOP","rowLabels":"NUMERIC","blockedSeats":[]}}
      """
    Then the response status is 201
    And I remember "pin" from the JSON field "pin"
    And I remember "roomId" from the JSON field "id"
    When I POST "/api/rooms/join" with body:
      """
      {"pin":"{pin}","pseudo":"Yann"}
      """
    Then the response status is 200
    And I remember "attendeeToken" from the JSON field "token"
    When I open a room WebSocket with the attendee token
    And I send the help request "stuck on step 3"
    Then the WebSocket receives a message containing "stuck on step 3"
    And the WebSocket receives a message containing "PENDING"
    And I remember "helpId" from the last help WebSocket message field "id"
    When I GET "/api/rooms/{roomId}/help"
    Then the response status is 200
    And the response body contains "stuck on step 3"
    And the response body contains "PENDING"
    When I POST "/api/rooms/{roomId}/help/{helpId}/claim"
    Then the response status is 200
    And the JSON field "status" is "CLAIMED"
    And the WebSocket receives a message containing "CLAIMED"
    When I POST "/api/rooms/{roomId}/help/{helpId}/resolve"
    Then the response status is 200
    And the JSON field "status" is "RESOLVED"
    And the WebSocket receives a message containing "RESOLVED"

  Scenario: A second raise while one is active is ignored (anti-spam), and an attendee can cancel
    Given a Keycloak token for user "grace"
    When I POST "/api/rooms" with body:
      """
      {"title":"Anti-spam lab","mode":"LAB","layout":{"rows":2,"blocks":[{"size":4,"label":"Center"}],"stagePos":"TOP","rowLabels":"NUMERIC","blockedSeats":[]}}
      """
    Then the response status is 201
    And I remember "pin" from the JSON field "pin"
    And I remember "roomId" from the JSON field "id"
    When I POST "/api/rooms/join" with body:
      """
      {"pin":"{pin}","pseudo":"Spammer"}
      """
    Then the response status is 200
    And I remember "attendeeToken" from the JSON field "token"
    When I open a room WebSocket with the attendee token
    And I send the help request "first call"
    Then the WebSocket receives a message containing "first call"
    When I send the help request "second call"
    And I cancel my help request
    Then the WebSocket receives a message containing "CANCELLED"
    When I GET "/api/rooms/{roomId}/help"
    Then the response status is 200
    And the response body does not contain "second call"
