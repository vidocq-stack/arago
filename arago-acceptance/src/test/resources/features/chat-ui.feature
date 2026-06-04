@ui
Feature: Attendee chat in the room view
  An attendee who joined a room can read and post chat messages in the SPA room view (the messages
  flow over the same room WebSocket, cf. arago-spec §4.3).

  Scenario: An attendee posts a chat message and sees it in the room
    Given I am logged in as superadmin
    When I POST "/api/admin/speakers" with body:
      """
      {"email":"speakerb@oidc.test","displayName":"Speaker B","role":"SPEAKER"}
      """
    Then the response status is 201
    Given a Keycloak token for user "speakerb"
    When I POST "/api/rooms" with body:
      """
      {"title":"Chat UI room","mode":"CONF"}
      """
    Then the response status is 201
    And I remember "pin" from the JSON field "pin"
    When I open the SPA at "/"
    And I fill "join-pin" with remembered "pin"
    And I fill "join-pseudo" with "Mia"
    And I click "join-submit"
    And I fill "chat-input" with "bonjour la room"
    And I click "chat-send"
    Then I see "bonjour la room"
    # A page refresh keeps the attendee in the room (session restored) and replays the chat.
    When I reload the page
    Then I see "bonjour la room"
