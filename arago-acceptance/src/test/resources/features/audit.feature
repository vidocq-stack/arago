Feature: Superadmin audit trail
  Superadmin actions are recorded to an audit trail, readable only with a superadmin token (spec §10.2).

  Scenario: Reading the audit trail requires a superadmin token
    When I GET "/api/admin/audit"
    Then the response status is 401

  Scenario: Provisioning a speaker is audited
    Given I am logged in as superadmin
    When I POST "/api/admin/speakers" with body:
      """
      {"email":"audited@example.com","role":"SPEAKER"}
      """
    Then the response status is 201
    When I GET "/api/admin/audit"
    Then the response status is 200
    And the response body contains "speaker.create"
