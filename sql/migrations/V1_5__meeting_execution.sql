-- =============================================================================
-- TimeSlot Database Schema v1.5 incremental migration
-- Owner    : meeting（会议执行域：参会人 / 出勤 / 个人通知）
-- Requires : v1.1（sql/migrations/V1_1__team_ready_baseline.sql）已执行，
--            即 schema.sql 快照已含 request_id 与 room_open_rule。
-- Scope    : 仅新增 meeting 域两张表，不修改任何既有表；
--            不写入 reservation / sys_user 等其他 Domain 数据。
-- Rollback : DROP TABLE notification; DROP TABLE reservation_attendee;
--            两表均为执行阶段事实数据，可安全回滚，不影响预约基线。
-- =============================================================================

USE meeting_room;

-- =============================================================================
-- 1. reservation_attendee 预约参与人与执行出勤表
--    职责：只记录预约执行阶段的事实（谁参与、是否签到/签退、是否缺席）。
--    约束：禁止本表触发 reservation.status 的任何更新（所有权归 reservation 域）。
--    状态机（应用层维护）：EXPECTED -> CHECKED_IN -> CHECKED_OUT；
--                         EXPECTED -> NO_SHOW（预约结束后由调度判定，幂等）。
--    组织者（预约创建人）以 attendee_role = 'ORGANIZER' 的行存在，
--    由应用层在首次触达执行流程时幂等补齐（INSERT IGNORE）。
-- =============================================================================
CREATE TABLE reservation_attendee (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    reservation_id    BIGINT       NOT NULL                COMMENT '所属预约ID（reservation 域，只读引用）',
    user_id           BIGINT       NOT NULL                COMMENT '参与人用户ID（identity 域，只读引用）',
    attendee_role     VARCHAR(20)  NOT NULL DEFAULT 'ATTENDEE' COMMENT '参会角色：ORGANIZER-组织者(预约创建人) / ATTENDEE-参与人',
    attendance_status VARCHAR(20)  NOT NULL DEFAULT 'EXPECTED' COMMENT '出勤状态：EXPECTED-待签到 / CHECKED_IN-已签到 / CHECKED_OUT-已签退 / NO_SHOW-缺席',
    check_in_at       DATETIME     NULL                    COMMENT '签到时间（服务器时间）',
    check_out_at      DATETIME     NULL                    COMMENT '签退时间（服务器时间）',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_attendee_reservation_user (reservation_id, user_id),
    KEY idx_attendee_user (user_id),
    CONSTRAINT fk_attendee_reservation FOREIGN KEY (reservation_id) REFERENCES reservation (id),
    CONSTRAINT fk_attendee_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT chk_attendee_role CHECK (attendee_role IN ('ORGANIZER', 'ATTENDEE')),
    CONSTRAINT chk_attendee_status CHECK (attendance_status IN ('EXPECTED', 'CHECKED_IN', 'CHECKED_OUT', 'NO_SHOW'))
) ENGINE = InnoDB COMMENT = '预约参与人与执行出勤表（meeting 域）';

-- =============================================================================
-- 2. notification 个人通知表
--    职责：meeting 域业务内通知（被加入/移出会议、会议开始提醒、No-Show 结果）。
--    幂等：uk_notification_dedup(user_id, dedup_key) + INSERT IGNORE，
--          调度类通知使用确定性 dedup_key，重复执行不产生重复通知。
--    预约取消/审批结果通知依赖 reservation/administration 域回调，属后续集成点，
--    本版本预留 type 取值，不擅自改动其他域代码。
-- =============================================================================
CREATE TABLE notification (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id        BIGINT       NOT NULL                COMMENT '接收人用户ID',
    type           VARCHAR(30)  NOT NULL                COMMENT '通知类型：ATTENDEE_ADDED/ATTENDEE_REMOVED/MEETING_REMINDER/NO_SHOW_MARKED（预留 MEETING_CANCELLED/APPROVAL_RESULT）',
    title          VARCHAR(100) NOT NULL                COMMENT '通知标题',
    content        VARCHAR(500) NOT NULL                COMMENT '通知内容',
    reservation_id BIGINT       NULL                    COMMENT '关联预约ID（可空；当前通知均源自会议执行）',
    dedup_key      VARCHAR(120) NOT NULL                COMMENT '业务幂等键：同一业务事实对同一用户只产生一条通知',
    is_read        TINYINT      NOT NULL DEFAULT 0      COMMENT '是否已读：0-未读 1-已读',
    read_at        DATETIME     NULL                    COMMENT '已读时间',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_dedup (user_id, dedup_key),
    KEY idx_notification_user_read (user_id, is_read, created_at),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE = InnoDB COMMENT = '个人通知表（meeting 域）';
