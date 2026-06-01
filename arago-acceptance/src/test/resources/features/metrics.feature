Feature: Metrics endpoint (MicroProfile Metrics via dirac)
  Arago exposes /metrics in OpenMetrics (Prometheus) text format for scraping (spec §5, §12).
  The endpoint is mounted at the standard root path by the dirac runtime extension and is
  unauthenticated (network-restricted in prod); cervantes leaves token-less requests anonymous.

  Scenario: The /metrics endpoint exposes OpenMetrics text
    When I GET "/metrics"
    Then the response status is 200
    And the response body contains "# EOF"
