-- TimeSlot Database Schema v1.4 incremental migration
-- Owner: resource
-- Prerequisite: v1.1 baseline (sql/schema.sql + sql/migrations/V1_1__team_ready_baseline.sql) applied.
-- Scope: resource domain management (maintenance plans + light repair tickets).
-- Room / category / facility / open rule tables already exist from v1.1 and are NOT altered here.

USE meeting_room;

-- 1. room_maintenance 会议室维护计划表（轻量：登记 + 结束，不做成资产系统）
CREATE TABLE room_maintenance (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    room_id    BIGINT       NOT NULL                COMMENT '会议室ID',
    reason     VARCHAR(200) NOT NULL                COMMENT '维护原因',
    start_time DATETIME     NOT NULL                COMMENT '维护开始时间',
    end_time   DATETIME     NOT NULL                COMMENT '维护结束时间',
    status     VARCHAR(20)  NOT NULL DEFAULT 'PLANNED' COMMENT '维护计划状态：PLANNED/FINISHED',
    created_by BIGINT       NOT NULL                COMMENT '创建管理员用户ID',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_maintenance_room (room_id),
    CONSTRAINT chk_maintenance_period CHECK (end_time > start_time),
    CONSTRAINT fk_maintenance_room FOREIGN KEY (room_id) REFERENCES meeting_room (id)
) ENGINE = InnoDB COMMENT = '会议室维护计划表';

-- 2. facility_repair_ticket 设施报修工单表（轻量：报修 + 解决）
CREATE TABLE facility_repair_ticket (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    room_id        BIGINT       NOT NULL               COMMENT '会议室ID',
    facility_id    BIGINT       NULL                   COMMENT '设施ID（空=整室报修）',
    facility_name  VARCHAR(50)  NOT NULL               COMMENT '设施名称',
    issue          VARCHAR(500) NOT NULL               COMMENT '故障描述',
    status         VARCHAR(20)  NOT NULL DEFAULT 'OPEN' COMMENT '工单状态：OPEN/RESOLVED',
    reporter_id    BIGINT       NOT NULL               COMMENT '报修人用户ID',
    reporter_name  VARCHAR(50)  NOT NULL               COMMENT '报修人姓名',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报修时间',
    resolved_at    DATETIME     NULL                   COMMENT '解决时间',
    resolve_remark VARCHAR(500) NULL                   COMMENT '处理说明',
    PRIMARY KEY (id),
    KEY idx_repair_room (room_id),
    KEY idx_repair_status (status),
    CONSTRAINT fk_repair_room FOREIGN KEY (room_id) REFERENCES meeting_room (id)
) ENGINE = InnoDB COMMENT = '设施报修工单表（轻量）';
