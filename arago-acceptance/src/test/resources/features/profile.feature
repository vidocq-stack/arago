Feature: Attendee RGPD self-service (magic link, access, portability, erasure) + retention purge
  An attendee who provided an email and posted a persistent question can, via a magic link (no password
  ever), see and export their data and exercise the right to be forgotten — which anonymises their
  messages and deletes their profile (cf. arago-spec §4.7). The superadmin can trigger the retention
  purge. No SMTP in tests: arago.mail.dev-expose-link returns the link in the response.

  Scenario: An attendee gets a magic link, reads, exports, then erases their data
    Given I am logged in as superadmin
    When I POST "/api/admin/speakers" with body:
      """
      {"email":"karl@oidc.test","displayName":"Karl Speaker","role":"SPEAKER"}
      """
    Then the response status is 201
    Given a Keycloak token for user "karl"
    When I POST "/api/rooms" with body:
      """
      {"title":"RGPD room","mode":"CONF"}
      """
    Then the response status is 201
    And I remember "pin" from the JSON field "pin"
    When I POST "/api/rooms/join" with body:
      """
      {"pin":"{pin}","pseudo":"Mallory","email":"attendee.rgpd@example.test","consentAccepted":true,"consentTextVersion":"v1"}
      """
    Then the response status is 200
    And I remember "attendeeToken" from the JSON field "token"
    When I open a room WebSocket with the attendee token
    And I send the persistent chat message "please keep this question"
    Then the WebSocket receives a message containing "please keep this question"
    # Magic link (dev-expose returns the token); then exercise the data-subject rights.
    When I POST "/api/profile/magic-link" with body:
      """
      {"email":"attendee.rgpd@example.test"}
      """
    Then the response status is 202
    And I remember "magicToken" from the JSON field "token"
    When I GET "/api/profile/me?token={magicToken}"
    Then the response status is 200
    And the JSON field "email" is "attendee.rgpd@example.test"
    And the response body contains "please keep this question"
    When I GET "/api/profile/me/export?token={magicToken}"
    Then the response status is 200
    When I DELETE "/api/profile/me?token={magicToken}"
    Then the response status is 200
    And the JSON field "messagesAnonymized" is 1
    And the response body contains "\"profileDeleted\":true"
    When I GET "/api/profile/me?token={magicToken}"
    Then the response status is 404

  Scenario: Requesting a magic link for an unknown email reveals nothing (anti-enumeration)
    When I POST "/api/profile/magic-link" with body:
      """
      {"email":"nobody.unknown@example.test"}
      """
    Then the response status is 202
    And the response body does not contain "token"

  Scenario: The purge endpoint requires a superadmin token
    When I POST "/api/admin/purge/run"
    Then the response status is 401

  Scenario: The superadmin can run the retention purge (idempotent)
    Given I am logged in as superadmin
    When I POST "/api/admin/purge/run"
    Then the response status is 200
    And the response body contains "ephemeralChatPurged"
