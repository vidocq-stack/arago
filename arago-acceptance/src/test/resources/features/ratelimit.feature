Feature: Superadmin login rate limiting
  Brute-force protection: too many login attempts from one client are throttled (spec §10.2).
  Each scenario uses a distinct client IP (X-Forwarded-For), so this does not affect other logins.

  Scenario: Repeated login attempts from one client are throttled
    When I POST "/api/admin/login" 6 times with body:
      """
      {"username":"root","password":"wrong-on-purpose"}
      """
    Then the response status is 429
