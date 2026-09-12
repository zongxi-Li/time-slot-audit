-- TimeSlot Database Schema v1.1 incremental migration
-- Owner: reservation + resource + identity
-- Prerequisite: v1.0 schema.sql has been applied.
-- Seed passwords are handled by data.sql; production credentials must never be plain text.

USE meeting_room;

-- 1. Normalise the old persisted COMPLETED value into CONFIRMED.
--    Completed is now derived from CONFIRMED and the current time.
UPDATE reservation
SET status = 'CONFIRMED'
WHERE status = 'COMPLETED';

-- 2. Add the idempotency key without breaking existing rows during backfill.
ALTER TABLE reservation
    ADD COLUMN request_id VARCHAR(100) NULL AFTER id;

UPDATE reservation
SET request_id = CONCAT('legacy-reservation-', id)
WHERE request_id IS NULL;

ALTER TABLE reservation
    MODIFY COLUMN request_id VARCHAR(100) NOT NULL COMMENT '客户端逻辑请求ID（幂等键）',
    ADD UNIQUE KEY uk_reservation_request_id (request_id);

-- 3. Add the minimal weekly opening-hours model.
CREATE TABLE room_open_rule (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    room_id     BIGINT       NOT NULL                COMMENT '会议室ID',
    weekday     TINYINT      NOT NULL                COMMENT '星期：1=周一 ... 7=周日',
    open_time   TIME         NOT NULL                COMMENT '开放时间（含）',
    close_time  TIME         NOT NULL                COMMENT '关闭时间（不含）',
    enabled     TINYINT      NOT NULL DEFAULT 1      COMMENT '是否启用：1-是 0-否',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_open_rule (room_id, weekday),
    KEY idx_open_rule_room_weekday (room_id, weekday),
    CONSTRAINT chk_open_rule_weekday CHECK (weekday BETWEEN 1 AND 7),
    CONSTRAINT chk_open_rule_period CHECK (close_time > open_time),
    CONSTRAINT fk_open_rule_room FOREIGN KEY (room_id) REFERENCES meeting_room (id)
) ENGINE = InnoDB COMMENT = '会议室每周开放时间规则';

