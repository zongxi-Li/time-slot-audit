-- =============================================================================
-- 会议室预约与时间冲突检查系统 —— 数据库建表脚本
-- -----------------------------------------------------------------------------
-- 目标数据库 : MySQL 8.x
-- 版本       : v1.4（V1_1 Team-Ready Baseline 2026-09-11 + V1_3 身份治理 + V1_4 资源管理 2026-09-15）
--              本文件始终表示从零初始化后的最新完整结构；存量库升级请按序执行
--              sql/migrations/ 下的增量脚本（见 docs/development/database-evolution.md）
-- 设计约定   : InnoDB / utf8mb4 / snake_case / BIGINT 主键 / DATETIME 时间
--              不使用 ENUM / 存储过程 / 触发器；
--              状态机取值(PENDING/CONFIRMED/...)由应用层维护，DB 只存字符串
-- 执行顺序   : schema.sql -> data.sql
-- =============================================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS meeting_room
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE meeting_room;

-- 重建时按外键依赖逆序删表（首次执行可忽略）
DROP TABLE IF EXISTS user_violation;
DROP TABLE IF EXISTS operation_log;
DROP TABLE IF EXISTS approval_record;
DROP TABLE IF EXISTS reservation;
DROP TABLE IF EXISTS facility_repair_ticket;
DROP TABLE IF EXISTS room_maintenance;
DROP TABLE IF EXISTS room_open_rule;
DROP TABLE IF EXISTS room_facility;
DROP TABLE IF EXISTS meeting_room;
DROP TABLE IF EXISTS room_category;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS department;

-- =============================================================================
-- 1. department 部门表（v1.3 身份治理）
--    职责：最小组织模型，仅列表/新增/修改；有用户挂靠时不提供删除。
-- =============================================================================
CREATE TABLE department (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    dept_name   VARCHAR(50)  NOT NULL                COMMENT '部门名称（唯一）',
    description VARCHAR(200) NULL                    COMMENT '部门说明',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_department_name (dept_name)
) ENGINE = InnoDB COMMENT = '部门表（最小组织模型）';

-- =============================================================================
-- 2. sys_user 系统用户表
--    职责：登录账号 + 角色（USER/ADMIN）+ 部门归属与预约资格推导。不做复杂 RBAC。
--    预约资格三要素：status（账号状态）、credit_score（信用分）、
--    restricted_until（限制截止时间，非空且在未来=黑名单/限制期）。
--    credit_score 默认 100（新用户视为正常）；资格门槛由应用层维护。
-- =============================================================================
CREATE TABLE sys_user (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username         VARCHAR(50)  NOT NULL                COMMENT '登录用户名（唯一）',
    password         VARCHAR(100) NOT NULL                COMMENT 'BCrypt 密码摘要，不保存明文密码',
    real_name        VARCHAR(50)  NOT NULL                COMMENT '真实姓名',
    email            VARCHAR(100) NULL                    COMMENT '邮箱',
    phone            VARCHAR(20)  NULL                    COMMENT '手机号',
    role             VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色：USER-普通用户 / ADMIN-管理员',
    department_id    BIGINT       NULL                    COMMENT '所属部门ID（可空）',
    credit_score     INT          NOT NULL DEFAULT 100    COMMENT '信用分（预约资格门槛由应用层维护）',
    restricted_until DATETIME     NULL                    COMMENT '限制截止时间（非空且在未来=黑名单/限制期）',
    status           TINYINT      NOT NULL DEFAULT 1      COMMENT '账号状态：1-正常 0-禁用',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    CONSTRAINT fk_user_department FOREIGN KEY (department_id) REFERENCES department (id)
) ENGINE = InnoDB COMMENT = '系统用户表';

-- =============================================================================
-- 3. user_violation 用户违规与信用记录表（v1.3 身份治理）
--    职责：只增不改，仅记录事实（扣分、加分、加黑、解除、禁用、启用）。
--    operator_id 为空表示系统自动触发（如信用分过低自动进入黑名单），
--    人工操作必须记录操作人；自动限制在信用恢复后自动解除，人工黑名单只能人工解除。
-- =============================================================================
CREATE TABLE user_violation (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id        BIGINT       NOT NULL                COMMENT '被记录的用户ID',
    violation_type VARCHAR(20)  NOT NULL                COMMENT '类型：CREDIT_DEDUCT-信用扣分 / CREDIT_REWARD-信用奖励 / BLACKLIST_SET-进入限制 / BLACKLIST_RELEASE-解除限制 / ACCOUNT_DISABLE-账号禁用 / ACCOUNT_ENABLE-账号启用',
    credit_change  INT          NOT NULL DEFAULT 0      COMMENT '信用分变化量（正数加分，负数扣分，与信用无关的记录为0）',
    reason         VARCHAR(500) NOT NULL                COMMENT '原因（人工操作必填，应用层校验）',
    operator_id    BIGINT       NULL                    COMMENT '操作人用户ID（NULL=系统自动）',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    PRIMARY KEY (id),
    KEY idx_violation_user (user_id, created_at),
    CONSTRAINT fk_violation_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE = InnoDB COMMENT = '用户违规与信用记录表';

-- =============================================================================
-- 4. room_category 会议室分类表
--    职责：定义会议室“类型 + 预约规则”。
--    是否需要审批由本表 approval_required 决定（分类级配置），与具体会议室无关。
-- =============================================================================
CREATE TABLE room_category (
    id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    category_name        VARCHAR(50)  NOT NULL                COMMENT '分类名称（唯一）：小型会议室/普通会议室/大型会议室/特殊会议室',
    min_capacity         INT          NOT NULL                COMMENT '分类容量下限（人）',
    max_capacity         INT          NOT NULL                COMMENT '分类容量上限（人）',
    approval_required    TINYINT      NOT NULL DEFAULT 0      COMMENT '是否需要审批：1-是（提交后PENDING，管理员审批） 0-否（提交后直接CONFIRMED）',
    max_duration_minutes INT          NOT NULL DEFAULT 240    COMMENT '单次预约最大时长（分钟），Service 校验',
    advance_days         INT          NOT NULL DEFAULT 7      COMMENT '允许提前预约的最大天数（天），Service 校验',
    description          VARCHAR(500) NULL                    COMMENT '分类说明',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_name (category_name)
) ENGINE = InnoDB COMMENT = '会议室分类表（类型与预约规则）';

-- =============================================================================
-- 5. meeting_room 会议室表
--    职责：具体会议室资源（A301、B502...）。容量 capacity 是“真实资源属性”，
--    预约人数校验以它为准；capacity 应落在所属分类 [min_capacity, max_capacity] 内，
--    由 Service 在创建/修改时校验（DB 不加跨表 CHECK）。
-- =============================================================================
CREATE TABLE meeting_room (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    category_id BIGINT       NOT NULL                COMMENT '所属分类ID（决定是否需要审批等规则）',
    room_name   VARCHAR(50)  NOT NULL                COMMENT '会议室名称（唯一，如A301）',
    location    VARCHAR(100) NULL                    COMMENT '位置（楼栋+楼层，如教学楼A栋3层）',
    capacity    INT          NOT NULL                COMMENT '实际容纳人数（真实资源属性）',
    status      TINYINT      NOT NULL DEFAULT 1      COMMENT '会议室状态：1-可用 0-维护中 2-停用',
    description VARCHAR(500) NULL                    COMMENT '会议室说明',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_name (room_name),
    KEY idx_room_category (category_id),
    CONSTRAINT fk_room_category FOREIGN KEY (category_id) REFERENCES room_category (id)
) ENGINE = InnoDB COMMENT = '会议室表（具体资源）';

-- =============================================================================
-- 6. room_facility 会议室设施表
--    职责：一个会议室的多条设施描述。设施为封闭小集合（投影仪/白板/视频会议设备/
--    麦克风等），无独立设施管理需求，故不拆 facility 字典表。
-- =============================================================================
CREATE TABLE room_facility (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    room_id       BIGINT       NOT NULL                COMMENT '所属会议室ID',
    facility_name VARCHAR(50)  NOT NULL                COMMENT '设施名称：投影仪/白板/视频会议设备/麦克风等',
    quantity      INT          NOT NULL DEFAULT 1      COMMENT '设施数量',
    description   VARCHAR(200) NULL                    COMMENT '设施说明',
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_facility (room_id, facility_name),
    CONSTRAINT fk_facility_room FOREIGN KEY (room_id) REFERENCES meeting_room (id)
) ENGINE = InnoDB COMMENT = '会议室设施表';

-- =============================================================================
-- 7. room_open_rule 会议室开放时间表
--    weekday 使用 ISO-8601：1=周一 ... 7=周日。
--    节假日和特殊日期不在 v1.1 范围内，未来通过独立 migration 扩展。
-- =============================================================================
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

-- =============================================================================
-- 8. room_maintenance 会议室维护计划表（v1.4 新增）
--    轻量登记：计划 PLANNED → 完成登记 FINISHED，不做成资产系统。
--    计划本身不阻断预约，需要阻断时将会议室状态置为 MAINTENANCE。
-- =============================================================================
CREATE TABLE room_maintenance (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    room_id     BIGINT       NOT NULL                COMMENT '会议室ID',
    reason      VARCHAR(200) NOT NULL                COMMENT '维护原因',
    start_time  DATETIME     NOT NULL                COMMENT '维护开始时间',
    end_time    DATETIME     NOT NULL                COMMENT '维护结束时间',
    status      VARCHAR(20)  NOT NULL DEFAULT 'PLANNED' COMMENT '维护计划状态：PLANNED/FINISHED',
    created_by  BIGINT       NOT NULL                COMMENT '创建管理员用户ID',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_maintenance_room (room_id),
    CONSTRAINT chk_maintenance_period CHECK (end_time > start_time),
    CONSTRAINT fk_maintenance_room FOREIGN KEY (room_id) REFERENCES meeting_room (id)
) ENGINE = InnoDB COMMENT = '会议室维护计划表';

-- =============================================================================
-- 9. facility_repair_ticket 设施报修工单表（v1.4 新增）
--    轻量闭环：用户报修 OPEN → 管理员处理 RESOLVED。
--    facility_name 为报修时快照，设施后续改名/删除不影响历史工单。
-- =============================================================================
CREATE TABLE facility_repair_ticket (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    room_id        BIGINT       NOT NULL               COMMENT '会议室ID',
    facility_id    BIGINT       NULL                   COMMENT '设施ID（空=整室报修）',
    facility_name  VARCHAR(50)  NOT NULL               COMMENT '设施名称（报修时快照）',
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

-- =============================================================================
-- 10. reservation 预约表（核心业务表）
--    状态机：PENDING / CONFIRMED / REJECTED / CANCELLED。
--    初始状态由分类的 approval_required 决定，故不设 DB 默认值。
--    version：改期使用乐观锁防止同一预约被静默覆盖；deleted 不设，
--    预约全程用状态管理生命周期，不物理删除。
--    冲突判定索引：idx_reservation_room_status_start
-- =============================================================================
CREATE TABLE reservation (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    request_id        VARCHAR(100) NOT NULL                COMMENT '客户端逻辑请求ID（幂等键）',
    reservation_no    VARCHAR(32)  NOT NULL                COMMENT '预约业务单号（服务端按 RSV+日期+序号 生成，用于展示与追溯）',
    room_id           BIGINT       NOT NULL                COMMENT '会议室ID',
    user_id           BIGINT       NOT NULL                COMMENT '预约人用户ID',
    title             VARCHAR(100) NOT NULL                COMMENT '会议主题',
    start_time        DATETIME     NOT NULL                COMMENT '预约开始时间',
    end_time          DATETIME     NOT NULL                COMMENT '预约结束时间（必须晚于开始时间，见chk_reservation_period）',
    participant_count INT          NOT NULL DEFAULT 1      COMMENT '参与人数（校验对象是会议室实际容量 capacity）',
    status            VARCHAR(20)  NOT NULL                COMMENT '状态：PENDING / CONFIRMED / REJECTED / CANCELLED（见chk_reservation_status）；PENDING与CONFIRMED占用时间段参与冲突检测',
    remark            VARCHAR(500) NULL                    COMMENT '预约备注（申请人填写）',
    cancel_reason     VARCHAR(500) NULL                    COMMENT '取消原因（仅用户取消或管理员强制取消时填写；审批驳回理由写 approval_record.remark）',
    version           INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本；每次预约写操作递增',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reservation_user_request (user_id, request_id),
    UNIQUE KEY uk_reservation_no (reservation_no),
    KEY idx_reservation_room_status_start (room_id, status, start_time),
    KEY idx_reservation_user_start (user_id, start_time),
    CONSTRAINT chk_reservation_period CHECK (end_time > start_time),
    CONSTRAINT chk_reservation_status CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT fk_reservation_room FOREIGN KEY (room_id) REFERENCES meeting_room (id),
    CONSTRAINT fk_reservation_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE = InnoDB COMMENT = '预约表（核心业务表）';

-- =============================================================================
-- 11. approval_record 审批记录表
--    职责：只保存审批“行为历史”（谁、对哪条预约、做了什么、何时）。
--    仅当受控分类（approval_required=1）的预约被管理员通过/驳回时产生；
--    普通会议室预约不产生审批记录。允许一个预约多条历史（不加唯一约束）。
-- =============================================================================
CREATE TABLE approval_record (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    reservation_id BIGINT       NOT NULL                COMMENT '预约ID',
    approver_id    BIGINT       NOT NULL                COMMENT '审批人（管理员）用户ID',
    action         VARCHAR(20)  NOT NULL                COMMENT '审批动作：APPROVE-通过 / REJECT-驳回',
    remark         VARCHAR(500) NULL                    COMMENT '审批意见（驳回时由应用层要求必填）',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审批时间（审批行为事实只记录在此，不在reservation上冗余approved_at）',
    PRIMARY KEY (id),
    KEY idx_approval_reservation (reservation_id),
    KEY idx_approval_approver (approver_id),
    CONSTRAINT fk_approval_reservation FOREIGN KEY (reservation_id) REFERENCES reservation (id),
    CONSTRAINT fk_approval_approver FOREIGN KEY (approver_id) REFERENCES sys_user (id)
) ENGINE = InnoDB COMMENT = '审批记录表（审批行为历史）';

-- =============================================================================
-- 12. operation_log 操作日志表
--    职责：只记录关键管理行为（审批/驳回、会议室增改、分类修改、强制取消等），
--    不记录所有 HTTP 请求。只增不改，不设 updated_at。
-- =============================================================================
-- =============================================================================
-- 13. system_time_config: singleton business clock configuration
-- =============================================================================
CREATE TABLE system_time_config (
    id          TINYINT      NOT NULL COMMENT 'singleton row; always 1',
    fixed_time  DATETIME     NULL     COMMENT 'NULL means real-time business clock',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_system_time_config_singleton CHECK (id = 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'business test clock configuration';

INSERT INTO system_time_config (id, fixed_time) VALUES (1, NULL);

-- =============================================================================
-- 14. booking_window_config: singleton admin-controlled bookable time window
-- =============================================================================
CREATE TABLE booking_window_config (
    id           TINYINT      NOT NULL COMMENT 'singleton row; always 1',
    start_minute INT          NOT NULL COMMENT '可预约开始，相对预约日期 00:00 的分钟数（含）',
    end_minute   INT          NOT NULL COMMENT '可预约结束，相对预约日期 00:00 的分钟数（含）；超过 1440 表示次日',
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_booking_window_singleton CHECK (id = 1),
    CONSTRAINT chk_booking_window_range CHECK (start_minute >= 0 AND start_minute < end_minute AND end_minute <= 2880)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '全局可预约时段配置';

-- 默认 08:00 - 次日 08:00（全天 24 小时可预约）
INSERT INTO booking_window_config (id, start_minute, end_minute) VALUES (1, 480, 1920);

CREATE TABLE operation_log (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id        BIGINT       NOT NULL                COMMENT '操作人用户ID',
    operation_type VARCHAR(50)  NOT NULL                COMMENT '操作类型：APPROVE_RESERVATION/REJECT_RESERVATION/FORCE_CANCEL_RESERVATION/CREATE_ROOM/UPDATE_ROOM/UPDATE_CATEGORY等',
    business_type  VARCHAR(50)  NOT NULL                COMMENT '业务类型：RESERVATION/MEETING_ROOM/ROOM_CATEGORY',
    business_id    BIGINT       NOT NULL                COMMENT '关联业务ID（预约ID/会议室ID/分类ID）',
    content        VARCHAR(500) NOT NULL                COMMENT '操作内容描述',
    ip_address     VARCHAR(50)  NULL                    COMMENT '操作IP',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_log_user (user_id),
    KEY idx_log_business (business_type, business_id),
    CONSTRAINT fk_log_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE = InnoDB COMMENT = '操作日志表（关键管理行为）';
