Feature: Superadmin login
  The break-glass superadmin account authenticates locally (no OIDC); its credentials come only from
  configuration. Valid credentials yield a signed token; bad credentials are rejected (cf. spec §4.8).

  Scenario: Valid credentials yield a token
    When I POST "/api/admin/login" with body:
      """
      {"username":"root","password":"correct-horse-battery"}
      """
    Then the response status is 200
    And the JSON field "token" is present

  Scenario: A wrong password is rejected
    When I POST "/api/admin/login" with body:
      """
      {"username":"root","password":"wrong-password"}
      """
    Then the response status is 401

  Scenario: An unknown user is rejected
    When I POST "/api/admin/login" with body:
      """
      {"username":"intruder","password":"correct-horse-battery"}
      """
    Then the response status is 401
