Feature: URL pin OpenGraph preview (Phase 2)
  Creating a URL pin fetches the page server-side (SSRF-hardened, best-effort) and stores its OpenGraph
  preview, exposed in the pin (cf. arago-spec §4.4). Tested against a local page (allow-private=true).

  Scenario: A URL pin is enriched with the page's OpenGraph title
    Given I am logged in as superadmin
    When I POST "/api/admin/speakers" with body:
      """
      {"email":"nina@oidc.test","displayName":"Nina Speaker","role":"SPEAKER"}
      """
    Then the response status is 201
    Given a local OpenGraph page titled "Arago Demo Page"
    Given a Keycloak token for user "nina"
    When I POST "/api/rooms" with body:
      """
      {"title":"Link room","mode":"CONF"}
      """
    Then the response status is 201
    And I remember "roomId" from the JSON field "id"
    When I POST "/api/rooms/{roomId}/pins" with body:
      """
      {"type":"URL","content":"{previewUrl}"}
      """
    Then the response status is 201
    And the JSON field "type" is "URL"
    And the response body contains "Arago Demo Page"
