Feature: Attendee email validation by magic link (Phase 2)
  An attendee's email is unvalidated until they follow a validation magic link. Their first persistent
  message is held (validated=false) until then; following the link marks the email validated and
  activates the held message (cf. arago-spec §4.7/§10.1). No SMTP in tests: the magic-link token is
  obtained via the dev-expose flow.

  Scenario: A persistent message is held until the attendee validates their email
    Given I am logged in as superadmin
    When I POST "/api/admin/speakers" with body:
      """
      {"email":"leo@oidc.test","displayName":"Leo Speaker","role":"SPEAKER"}
      """
    Then the response status is 201
    Given a Keycloak token for user "leo"
    When I POST "/api/rooms" with body:
      """
      {"title":"Validation room","mode":"CONF"}
      """
    Then the response status is 201
    And I remember "pin" from the JSON field "pin"
    When I POST "/api/rooms/join" with body:
      """
      {"pin":"{pin}","pseudo":"Trent","email":"attendee.emv@example.test","consentAccepted":true,"consentTextVersion":"v1"}
      """
    Then the response status is 200
    And I remember "attendeeToken" from the JSON field "token"
    When I open a room WebSocket with the attendee token
    And I send the persistent chat message "question à valider"
    Then the WebSocket receives a message containing "question à valider"
    # Pull the profile token (dev-expose), then read the data: the message is held pending validation.
    When I POST "/api/profile/magic-link" with body:
      """
      {"email":"attendee.emv@example.test"}
      """
    Then the response status is 202
    And I remember "magicToken" from the JSON field "token"
    When I GET "/api/profile/me?token={magicToken}"
    Then the response status is 200
    And the response body contains "\"validated\":false"
    # Follow the validation link → email validated, held message activated.
    When I GET "/api/profile/validate?token={magicToken}"
    Then the response status is 200
    And the JSON field "messagesActivated" is 1
    When I GET "/api/profile/me?token={magicToken}"
    Then the response status is 200
    And the response body contains "\"validated\":true"
