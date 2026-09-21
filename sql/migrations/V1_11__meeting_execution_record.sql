-- =============================================================================
-- TimeSlot Database Schema v1.11 incremental migration
-- Owner    : meeting（会议执行域：会议级实际使用记录）
-- Requires : v1.5（reservation_attendee / notification 已存在）
-- Scope    : 新增一张与 reservation 一对一的实际使用记录表；不修改预约计划时间。
-- Rollback : DROP TABLE meeting_execution;
-- =============================================================================

USE meeting_room;

CREATE TABLE meeting_execution (
    reservation_id        BIGINT NOT NULL                COMMENT '预约ID（一对一）',
    actual_start_time     DATETIME NOT NULL              COMMENT '实际开始时间',
    actual_end_time       DATETIME NOT NULL              COMMENT '实际结束时间',
    actual_attendee_count INT      NOT NULL              COMMENT '实际参会人数，可为0',
    recorded_by           BIGINT NOT NULL                COMMENT '登记人用户ID（组织者或管理员）',
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次登记时间',
    updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修正时间',
    PRIMARY KEY (reservation_id),
    KEY idx_execution_recorded_by (recorded_by),
    CONSTRAINT chk_execution_period CHECK (actual_end_time > actual_start_time),
    CONSTRAINT chk_execution_attendee_count CHECK (actual_attendee_count >= 0),
    CONSTRAINT fk_execution_reservation FOREIGN KEY (reservation_id) REFERENCES reservation (id),
    CONSTRAINT fk_execution_recorded_by FOREIGN KEY (recorded_by) REFERENCES sys_user (id)
) ENGINE = InnoDB COMMENT = '会议实际使用记录（meeting 域）';
