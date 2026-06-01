Feature: Room lifecycle (speaker)
  Speakers create/own/end rooms via /api/rooms (Cassini → Mansart/PostgreSQL). Ownership is the
  speaker's OIDC subject; only provisioned (allowlisted) speakers may create. PIN is a 6-digit code
  attendees will use to join (cf. arago-spec §4.1, §8). The count endpoint is the Phase 0 baseline.

  Scenario: An empty database reports zero rooms
    When I GET "/api/rooms/count"
    Then the response status is 200
    And the JSON field "total" is 0
    And the JSON field "active" is 0

  Scenario: Unauthenticated room creation is rejected
    When I POST "/api/rooms" with body:
      """
      {"title":"No token"}
      """
    Then the response status is 401

  Scenario: A non-provisioned speaker cannot create a room
    Given a Keycloak token for user "bob"
    When I POST "/api/rooms" with body:
      """
      {"title":"Bob is not on the allowlist"}
      """
    Then the response status is 403

  Scenario: A provisioned speaker creates, fetches and ends a room
    Given I am logged in as superadmin
    When I POST "/api/admin/speakers" with body:
      """
      {"email":"carol@oidc.test","displayName":"Carol Speaker","role":"SPEAKER"}
      """
    Then the response status is 201
    Given a Keycloak token for user "carol"
    When I POST "/api/rooms" with body:
      """
      {"title":"Carol's Talk","mode":"CONF"}
      """
    Then the response status is 201
    And the JSON field "status" is "ACTIVE"
    And the JSON field "mode" is "CONF"
    And the JSON field "title" is "Carol's Talk"
    And the JSON field "pin" is present
    And I remember "roomId" from the JSON field "id"
    When I GET "/api/rooms/{roomId}"
    Then the response status is 200
    And the JSON field "title" is "Carol's Talk"
    When I POST "/api/rooms/{roomId}/end"
    Then the response status is 200
    And the JSON field "status" is "ENDED"
