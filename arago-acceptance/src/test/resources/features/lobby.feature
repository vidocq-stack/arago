Feature: Public room lobby / projector screen
  A speaker projects an attendee-facing screen showing the room title + PIN, with a headcount that
  updates as attendees join. The lobby endpoint is public (the PIN is the join credential anyone is
  shown) and reports the number of attendees currently connected to the room WebSocket (arago-spec §4.1).

  Scenario: The lobby of an unknown PIN is 404 and needs no authentication
    When I GET "/api/rooms/lobby/000000"
    Then the response status is 404

  Scenario: The public lobby shows the room title, PIN and a live headcount
    Given I am logged in as superadmin
    When I POST "/api/admin/speakers" with body:
      """
      {"email":"speakera@oidc.test","displayName":"Speaker A","role":"SPEAKER"}
      """
    Then the response status is 201
    Given a Keycloak token for user "speakera"
    When I POST "/api/rooms" with body:
      """
      {"title":"Lobby Talk","mode":"CONF"}
      """
    Then the response status is 201
    And I remember "pin" from the JSON field "pin"
    When I POST "/api/rooms/join" with body:
      """
      {"pin":"{pin}","pseudo":"Zoe"}
      """
    Then the response status is 200
    And I remember "attendeeToken" from the JSON field "token"
    # Anonymous (projector) read: no one connected yet.
    When I forget my token
    And I GET "/api/rooms/lobby/{pin}"
    Then the response status is 200
    And the JSON field "title" is "Lobby Talk"
    And the JSON field "attendees" is 0
    # Once an attendee is connected (confirmed by the chat broadcast), the headcount is 1.
    When I open a room WebSocket with the attendee token
    And I send the chat message "here"
    Then the WebSocket receives a message containing "here"
    When I GET "/api/rooms/lobby/{pin}"
    Then the response status is 200
    And the JSON field "attendees" is 1
