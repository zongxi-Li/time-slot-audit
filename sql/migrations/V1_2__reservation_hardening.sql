-- TimeSlot Database Schema v1.2 incremental migration
-- Owner: reservation
-- Prerequisite: v1.1 (V1_1__team_ready_baseline.sql) has been applied.
-- Scope: reservation idempotency key narrows from global scope to per-user scope.
-- Rollback risk: index replacement only; no column or data change, so rollback is a
-- symmetric ALTER (drop uk_reservation_user_request, recreate uk_reservation_request_id).
-- Verification: SHOW INDEX FROM reservation WHERE Key_name = 'uk_reservation_user_request';

USE meeting_room;

-- Idempotency is per-user: the same requestId from two different users must be able
-- to create reservations independently, so the global unique key
-- uk_reservation_request_id(request_id) is replaced by (user_id, request_id).
-- Existing rows already satisfy the new key because request_id was globally unique.
ALTER TABLE reservation
    DROP KEY uk_reservation_request_id,
    ADD UNIQUE KEY uk_reservation_user_request (user_id, request_id);
