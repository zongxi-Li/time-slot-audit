-- V1_8__reservation_optimistic_lock.sql
-- Owner: reservation
-- Prerequisite: V1_1 through V1_7 have been applied.
-- Purpose: prevent concurrent edits of the same reservation from silently
--          overwriting one another. Existing rows start at version 0.
-- Rollback: ALTER TABLE reservation DROP COLUMN version;

ALTER TABLE reservation
    ADD COLUMN version INT NOT NULL DEFAULT 0
        COMMENT '乐观锁版本；每次预约写操作递增'
        AFTER cancel_reason;
