Feature: OIDC identity and allowlist enforcement
  Speakers authenticate via Keycloak (OIDC, Authorization: Bearer); authorization is the local
  allowlist (spec §4.2/§4.8). The superadmin token rides a distinct X-Arago-Admin header, so the two
  Bearer issuers coexist (ARAGO-004): cervantes validates only the Keycloak Bearer.

  Scenario: Unauthenticated access to the identity endpoint is rejected
    When I GET "/api/oidc/me"
    Then the response status is 401

  Scenario: A provisioned speaker resolves their identity
    Given I am logged in as superadmin
    When I POST "/api/admin/speakers" with body:
      """
      {"email":"ada@oidc.test","displayName":"Ada Lovelace","role":"SPEAKER"}
      """
    Then the response status is 201
    Given a Keycloak token for user "ada"
    When I GET "/api/oidc/me"
    Then the response status is 200
    And the JSON field "email" is "ada@oidc.test"

  Scenario: An authenticated but non-provisioned speaker is forbidden
    Given a Keycloak token for user "bob"
    When I GET "/api/oidc/me"
    Then the response status is 403
    And the JSON field "error" is "speaker_not_provisioned"
