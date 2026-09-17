-- TimeSlot Database Schema v1.7 incremental migration
-- Owner: reservation
-- Prerequisite: v1.1 ~ v1.6 (V1_1..V1_6) have been applied.
-- Scope: reservation.status gets a CHECK constraint so MySQL itself rejects any
--        value outside the four persisted lifecycle states. Display states
--        (UPCOMING / IN_USE / COMPLETED) are computed at read time and must
--        never be stored.
-- Rollback risk: constraint only; rollback is a symmetric
--        ALTER TABLE reservation DROP CHECK chk_reservation_status.
-- Verification: SHOW CREATE TABLE reservation;  -- expect chk_reservation_status

USE meeting_room;

ALTER TABLE reservation
    ADD CONSTRAINT chk_reservation_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED'));
