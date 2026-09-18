# Business Test Clock

`GET /api/system-time` returns the authoritative business time for every signed-in user.

- `PUT /api/admin/system-time` (ADMIN) accepts `{"currentTime":"2026-09-18T10:30:00"}` and persists the fixed clock in `system_time_config`.
- `DELETE /api/admin/system-time` (ADMIN) clears the fixed value and restores real-time mode.

Reservation lifecycle guards, meeting execution scheduling, qualification checks, and the operational UI state-machine projection all consume this clock. The browser refreshes its affected data immediately after an administrator changes the clock. JWT expiration intentionally stays on wall-clock time so a test-time jump cannot invalidate the active login.
