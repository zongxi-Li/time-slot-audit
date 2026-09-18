-- V1_9__system_time_config.sql
-- Owner: common
-- Purpose: persist the single adjustable business clock used for lifecycle testing.
-- Rollback: DROP TABLE system_time_config;

CREATE TABLE system_time_config (
    id          TINYINT NOT NULL COMMENT 'singleton row; always 1',
    fixed_time  DATETIME NULL COMMENT 'NULL means realtime; non-NULL means fixed test time',
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_system_time_config_singleton CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='business test clock configuration';

INSERT INTO system_time_config (id, fixed_time)
VALUES (1, NULL);
