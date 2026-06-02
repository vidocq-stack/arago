@ui
Feature: Speaker OIDC login (front-channel, Authorization Code + PKCE)
  A provisioned speaker logs in through the real Keycloak login page (server-driven Auth Code + PKCE,
  cf. arago-spec §7/§8); the SPA then shows their identity (proving /api/oidc/me accepts the token).
  A non-provisioned user is told so. Closes the Phase 1 O1 front-channel.

  Scenario: A provisioned speaker logs in via Keycloak and the SPA shows their identity
    Given I am logged in as superadmin
    When I POST "/api/admin/speakers" with body:
      """
      {"email":"judy@oidc.test","displayName":"Judy Speaker","role":"SPEAKER"}
      """
    Then the response status is 201
    When I open the SPA at "/"
    And I click "speaker-login"
    And I log in to Keycloak as "judy" with password "pw"
    Then I see "judy@oidc.test"

  Scenario: A non-provisioned user is told they are not provisioned
    When I open the SPA at "/"
    And I click "speaker-login"
    And I log in to Keycloak as "bob" with password "pw"
    Then I see "non provisionné"
