Feature: Rooms count
  The REST API (Cassini) is mounted under /api and backed by Mansart/PostgreSQL. Baseline from Phase 0.

  Scenario: An empty database reports zero rooms
    When I GET "/api/rooms/count"
    Then the response status is 200
    And the JSON field "total" is 0
    And the JSON field "active" is 0
