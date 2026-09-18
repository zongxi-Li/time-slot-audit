-- V1_10__booking_window_config.sql
-- Owner: common
-- Purpose: persist the single admin-controlled bookable time window. The window is
--          expressed in minutes counted from the reserved date's 00:00; end_minute
--          may exceed 1440 to reach into the next day (e.g. 480-1920 = 08:00-次日08:00).
-- Rollback: DROP TABLE booking_window_config;

CREATE TABLE booking_window_config (
    id           TINYINT NOT NULL COMMENT 'singleton row; always 1',
    start_minute INT     NOT NULL COMMENT '可预约开始，相对预约日期 00:00 的分钟数（含）',
    end_minute   INT     NOT NULL COMMENT '可预约结束，相对预约日期 00:00 的分钟数（含）；超过 1440 表示次日',
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_booking_window_singleton CHECK (id = 1),
    CONSTRAINT chk_booking_window_range CHECK (start_minute >= 0 AND start_minute < end_minute AND end_minute <= 2880)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局可预约时段配置';

-- 默认 08:00 - 次日 08:00（全天 24 小时可预约）
INSERT INTO booking_window_config (id, start_minute, end_minute)
VALUES (1, 480, 1920);
