Feature: Health endpoints
  MicroProfile Health (knock) is served at the standard root paths. Baseline established in Phase 0.

  Scenario: The aggregate health endpoint reports UP
    When I GET "/health"
    Then the response status is 200
    And the JSON field "status" is "UP"

  Scenario Outline: Each health probe is reachable
    When I GET "<path>"
    Then the response status is 200

    Examples:
      | path            |
      | /health/live    |
      | /health/ready   |
      | /health/started |
