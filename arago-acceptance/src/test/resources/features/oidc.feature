Feature: OIDC identity and allowlist enforcement
  Speakers authenticate via Keycloak (OIDC); authorization is the local allowlist (spec §4.2/§4.8).
  The 200/403 cases (real Keycloak token) live in a Keycloak-Testcontainer scenario; this covers the
  unauthenticated case, which needs no issuer.

  Scenario: Unauthenticated access to the identity endpoint is rejected
    When I GET "/api/oidc/me"
    Then the response status is 401
