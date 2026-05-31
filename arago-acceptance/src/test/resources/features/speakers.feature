Feature: Speaker allowlist administration
  The superadmin manages the speaker allowlist (spec §4.8). Every endpoint requires a superadmin token.

  Scenario: Listing the allowlist requires a superadmin token
    When I GET "/api/admin/speakers"
    Then the response status is 401

  Scenario: Superadmin provisions, lists, updates and removes a speaker
    Given I am logged in as superadmin
    When I POST "/api/admin/speakers" with body:
      """
      {"email":"Ada@Example.com","role":"SPEAKER","displayName":"Ada"}
      """
    Then the response status is 201
    And the JSON field "email" is "ada@example.com"
    And the JSON field "id" is present

    When I GET "/api/admin/speakers"
    Then the response status is 200

    When I POST "/api/admin/speakers" with body:
      """
      {"email":"grace@example.com","role":"ADMIN"}
      """
    Then the response status is 201

  Scenario: Provisioning the same email twice is a conflict
    Given I am logged in as superadmin
    When I POST "/api/admin/speakers" with body:
      """
      {"email":"dup@example.com","role":"SPEAKER"}
      """
    Then the response status is 201
    When I POST "/api/admin/speakers" with body:
      """
      {"email":"dup@example.com","role":"ADMIN"}
      """
    Then the response status is 409
