-- =============================================================================
-- 会议室预约与时间冲突检查系统 —— 一体化建库脚本（结构 + 种子数据）
-- -----------------------------------------------------------------------------
-- 目标数据库 : MySQL 8.x
-- 版本       : v1.11（整合 v1.1 ~ v1.11 的全部结构变更）
-- 脚本内容   : 1) 创建 meeting_room 数据库；
--              2) 按外键依赖逆序重建全部 17 张业务表（含主键、索引、外键与 CHECK 约束）；
--              3) 写入演示/测试种子数据。
-- 覆盖范围   : 等价于 sql/schema.sql（完整快照）+ sql/data.sql（种子数据），
--              并补齐 V1_6 迁移增加、而快照遗漏的 4 个运营查询索引。
-- 适用场景   : 从零搭建本地或演示数据库。脚本会 DROP 并重建全部业务表，
--              已有业务数据的库请勿执行，应改用 sql/migrations/ 下的增量脚本。
-- 执行方式   : mysql --default-character-set=utf8mb4 -u<user> -p -e "source sql/init.sql"
--              也可在 MySQL 客户端内执行：source C:/path/to/sql/init.sql
--              （Windows PowerShell 下不要用 Get-Content ... | mysql 管道，
--               管道会按本地代码页重编码，破坏 UTF-8 中文注释导致语法错误）
-- 设计约定   : InnoDB / utf8mb4 / snake_case / BIGINT 主键 / DATETIME 时间；
--              不使用 ENUM / 存储过程 / 触发器，状态机取值由应用层维护
-- 幂等性     : 可重复执行；每次执行都会重建表结构并重新装载种子数据
-- =============================================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS meeting_room
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE meeting_room;

-- 重建时按外键依赖逆序删表（首次执行可忽略）
DROP TABLE IF EXISTS user_violation;
DROP TABLE IF EXISTS operation_log;
DROP TABLE IF EXISTS notification;
DROP TABLE IF EXISTS reservation_attendee;
DROP TABLE IF EXISTS meeting_execution;
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
DROP TABLE IF EXISTS system_time_config;
DROP TABLE IF EXISTS booking_window_config;

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
    max_duration_minutes INT          NOT NULL DEFAULT 1440   COMMENT '单次预约最大时长（分钟），Service 校验',
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
    KEY idx_reservation_status_period (status, start_time, end_time),  -- V1_6 运营统计索引
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
    KEY idx_approval_created_at (created_at),                          -- V1_6 运营统计索引
    CONSTRAINT fk_approval_reservation FOREIGN KEY (reservation_id) REFERENCES reservation (id),
    CONSTRAINT fk_approval_approver FOREIGN KEY (approver_id) REFERENCES sys_user (id)
) ENGINE = InnoDB COMMENT = '审批记录表（审批行为历史）';

-- =============================================================================
-- 12. meeting_execution 会议实际使用记录（v1.11）
--    职责：保存一场预约的实际开始时间、实际结束时间和实际参会人数。
--    reservation.start_time/end_time 仍是计划时间，本表只记录执行结果；
--    一条预约最多一条记录，重复登记表示修正结果，不产生重复历史行。
-- =============================================================================
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

-- =============================================================================
-- 13. reservation_attendee 预约参与人与执行出勤表（v1.5 会议执行）
--    职责：只记录预约执行阶段的事实（谁参与、是否签到/签退、是否缺席）；
--    禁止本表触发 reservation.status 的任何更新（所有权归预约域）。
--    出勤状态机（应用层维护）：EXPECTED -> CHECKED_IN -> CHECKED_OUT；
--    EXPECTED -> NO_SHOW（预约结束后由调度判定，幂等）。
--    组织者（预约创建人）以 attendee_role = 'ORGANIZER' 的行存在，
--    由应用层在首次触达执行流程时幂等补齐（INSERT IGNORE）。
-- =============================================================================
CREATE TABLE reservation_attendee (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    reservation_id    BIGINT       NOT NULL                COMMENT '所属预约ID（预约域，只读引用）',
    user_id           BIGINT       NOT NULL                COMMENT '参与人用户ID（身份域，只读引用）',
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
-- 14. notification 个人通知表（v1.5 会议执行）
--    职责：meeting 域业务内通知（被加入/移出会议、会议开始提醒、No-Show 结果）。
--    幂等：uk_notification_dedup(user_id, dedup_key) + INSERT IGNORE，
--    调度类通知使用确定性 dedup_key，重复执行不产生重复通知。
--    预约取消/审批结果通知依赖预约/管理域回调，type 取值预留。
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

-- =============================================================================
-- 15. operation_log 操作日志表
--    职责：只记录关键管理行为（审批/驳回、会议室增改、分类修改、强制取消等），
--    不记录所有 HTTP 请求。只增不改，不设 updated_at。
-- =============================================================================
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
    KEY idx_operation_business_created (business_type, created_at),    -- V1_6 运营统计索引
    KEY idx_operation_user_created (user_id, created_at),              -- V1_6 运营统计索引
    CONSTRAINT fk_log_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE = InnoDB COMMENT = '操作日志表（关键管理行为）';

-- =============================================================================
-- 16. system_time_config: singleton business clock configuration
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
-- 17. booking_window_config: singleton admin-controlled bookable time window
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

-- =============================================================================
-- 第二部分：演示 / 测试种子数据
--   说明：预约时间基于 CURDATE() 相对生成，任何时候导入都落在未来区间，
--         可直接联调“待审批 / 可取消 / 可登记执行记录”等流程。
-- =============================================================================

-- 清空旧数据（逆序清空，避免外键干扰）
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE user_violation;
TRUNCATE TABLE operation_log;
TRUNCATE TABLE meeting_execution;
TRUNCATE TABLE notification;
TRUNCATE TABLE reservation_attendee;
TRUNCATE TABLE approval_record;
TRUNCATE TABLE reservation;
TRUNCATE TABLE room_open_rule;
TRUNCATE TABLE room_facility;
TRUNCATE TABLE meeting_room;
TRUNCATE TABLE room_category;
TRUNCATE TABLE sys_user;
TRUNCATE TABLE department;
SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 0. 部门：最小组织模型演示数据（v1.3 身份治理）
-- =============================================================================
INSERT INTO department (id, dept_name, description) VALUES
(1, '信息中心',   '校园信息化建设与运维部门'),
(2, '软件学院',   '软件工程专业教学单位'),
(3, '后勤保障处', '场地与后勤保障部门');

-- =============================================================================
-- 1. 用户：1 管理员 + 2 普通用户（信用分与违规记录保持一致，见第 8 节）
-- =============================================================================
INSERT INTO sys_user (id, username, password, real_name, email, phone, role, department_id, credit_score, status) VALUES
(1, 'admin',    '$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm', '系统管理员', 'admin@timeslot.demo',    '13800000001', 'ADMIN', 1,    100, 1),
(2, 'zhangsan', '$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm', '张三',       'zhangsan@timeslot.demo', '13800000002', 'USER',  2,    110, 1),
(3, 'lisi',     '$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm', '李四',       'lisi@timeslot.demo',     '13800000003', 'USER',  1,    80,  1);

-- =============================================================================
-- 2. 会议室分类：审批开关配置在分类上
--    小型/普通免审批（提交即 CONFIRMED），大型/特殊需审批（提交进 PENDING）
-- =============================================================================
INSERT INTO sys_user (id, username, password, real_name, email, phone, role, department_id, credit_score, status) VALUES
(4, 'lzx', '$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm', 'LZX', 'lzx@timeslot.demo', '13800000004', 'ADMIN', 1, 100, 1);

INSERT INTO room_category (id, category_name, min_capacity, max_capacity, approval_required, max_duration_minutes, advance_days, description) VALUES
(1, '小型会议室', 3,   8,   0, 1440, 7,  '3-8人日常讨论，免审批，单次最长24小时，可提前7天预约'),
(2, '普通会议室', 9,   20,  0, 1440, 7,  '9-20人常规会议，免审批，单次最长24小时，可提前7天预约'),
(3, '大型会议室', 21,  100, 1, 1440, 14, '21人以上大型会议，需管理员审批，单次最长24小时，可提前14天预约'),
(4, '特殊会议室', 10,  60,  1, 1440, 14, '路演厅、多功能厅等受控场地，需管理员审批，单次最长24小时，可提前14天预约');

-- =============================================================================
-- 3. 会议室：6 间（容量均落在所属分类 [min_capacity, max_capacity] 区间内）
-- =============================================================================
INSERT INTO meeting_room (id, category_id, room_name, location, capacity, status, description) VALUES
(1, 1, 'A301', '教学楼A栋3层', 8,  1, '4-8人圆桌讨论间，配白板'),
(2, 1, 'A302', '教学楼A栋3层', 6,  1, '6人小组讨论间'),
(3, 2, 'B201', '教学楼B栋2层', 16, 1, '常规投影会议室'),
(4, 2, 'B202', '教学楼B栋2层', 20, 1, '支持视频会议的常规会议室'),
(5, 3, 'B502', '教学楼B栋5层', 60, 1, '阶梯报告厅，大型会议场地'),
(6, 4, 'S101', '综合楼1层',    40, 1, '路演厅，含灯光音响，使用需审批');

-- =============================================================================
-- 4. 会议室设施
-- =============================================================================
INSERT INTO meeting_room (id, category_id, room_name, location, capacity, status, description) VALUES
(7,  1, 'C101', 'Teaching Building C, 1F',  8,  1, 'Small team room with projector and whiteboard'),
(8,  2, 'C201', 'Teaching Building C, 2F', 18,  1, 'Standard meeting room with video equipment'),
(9,  3, 'D301', 'Teaching Building D, 3F', 50,  1, 'Large lecture room for department events'),
(10, 4, 'D401', 'Teaching Building D, 4F', 35,  1, 'Multi-purpose presentation room'),
(11, 2, 'E201', 'Library Building E, 2F', 16,  1, 'Quiet discussion room');

-- =============================================================================
-- 4. 会议室设施：覆盖任务书"投影、视频会议、白板等"并扩充种类，
--    统一中文命名便于按关键字筛选（如 投影 / 白板 / 麦克风 / 投屏）。
-- =============================================================================
INSERT INTO room_facility (id, room_id, facility_name, quantity, description) VALUES
(1,  1, '投影仪',       1, NULL),
(2,  1, '白板',         1, NULL),
(3,  1, '无线投屏',     1, NULL),
(4,  2, '显示屏',       1, '55寸电视'),
(5,  2, '白板',         1, NULL),
(6,  3, '投影仪',       1, NULL),
(7,  3, '麦克风',       2, '无线手持'),
(8,  3, '讲台电脑',     1, NULL),
(9,  4, '投影仪',       1, NULL),
(10, 4, '视频会议设备', 1, NULL),
(11, 4, '白板',         1, NULL),
(12, 5, '投影仪',       2, NULL),
(13, 5, '视频会议设备', 1, NULL),
(14, 5, '无线麦克风',   4, NULL),
(15, 5, '音响系统',     1, NULL),
(16, 6, '视频会议设备', 1, NULL),
(17, 6, '音响系统',     1, NULL),
(18, 6, '舞台灯光',     1, '路演用，需管理员协助开启'),
(19, 6, '无线投屏',     1, NULL),
(20, 7, '投影仪',       1, '吸顶安装'),
(21, 7, '白板',         1, NULL),
(22, 7, '电子白板',     1, NULL),
(23, 8, '投影仪',       1, NULL),
(24, 8, '视频会议设备', 1, NULL),
(25, 8, '讲台电脑',     1, NULL),
(26, 9, '投影仪',       2, NULL),
(27, 9, '无线麦克风',   4, NULL),
(28, 9, '视频摄像头',   1, '录播用'),
(29, 10, '音响系统',    1, NULL),
(30, 10, '舞台灯光',    1, NULL),
(31, 10, '麦克风',      2, NULL),
(32, 11, '显示屏',      1, '65寸显示屏'),
(33, 11, '白板',        1, NULL),
(34, 11, '无线投屏',    1, NULL);

-- =============================================================================
-- 5. 每间会议室每周开放时间：周一至周日 08:00-19:00。
--    课程基线不包含节假日和特殊日期规则。
-- =============================================================================

INSERT INTO room_open_rule (room_id, weekday, open_time, close_time, enabled)
SELECT r.id, d.weekday, '08:00:00', '19:00:00', 1
FROM meeting_room r
CROSS JOIN (
    SELECT 1 AS weekday UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
    SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
) d;

-- =============================================================================
-- 6. 预约：覆盖四个持久化状态；已结束通过 CONFIRMED + 时间动态推导
--    room_id=3 B201 : CONFIRMED（普通会议室，免审批直接确认）
--    room_id=5 B502 : PENDING   （大型会议室，待管理员审批）
--    room_id=6 S101 : CONFIRMED + REJECTED（特殊会议室，含审批历史）
--    room_id=1 A301 : CANCELLED（管理员强制取消，带取消原因）
--    room_id=4 B202 : CONFIRMED（过去的会议，API 动态展示为 COMPLETED）
-- =============================================================================
INSERT INTO reservation (id, request_id, reservation_no, room_id, user_id, title, start_time, end_time, participant_count, status, remark, cancel_reason) VALUES
(1, 'seed-rsv-1', 'RSV20260901001', 3, 2, '项目周会',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '10:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '11:30:00'),
   12, 'CONFIRMED', '需要投影仪', NULL),
(2, 'seed-rsv-2', 'RSV20260901002', 5, 3, '全院月度总结会',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '14:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '16:00:00'),
   45, 'PENDING', '需提前调试话筒', NULL),
(3, 'seed-rsv-3', 'RSV20260901003', 6, 2, '新产品内部路演',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '09:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '11:00:00'),
   30, 'CONFIRMED', '需要灯光和音响', NULL),
(4, 'seed-rsv-4', 'RSV20260901004', 6, 3, '社团招新宣讲',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '14:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '16:00:00'),
   35, 'REJECTED', NULL, NULL),
(5, 'seed-rsv-5', 'RSV20260901005', 1, 3, '小组讨论',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '15:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '16:00:00'),
   5, 'CANCELLED', NULL, '该时段安排设备检修，管理员强制取消'),
(6, 'seed-rsv-6', 'RSV20260901006', 4, 2, '迭代评审会',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '10:00:00'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '11:30:00'),
   15, 'CONFIRMED', NULL, NULL);

-- =============================================================================
-- 6. 审批记录：仅特殊会议室（受控分类）的预约产生
--    id=3 已审批通过；id=4 已驳回；id=2 仍为 PENDING，尚无审批记录
-- =============================================================================
INSERT INTO reservation (id, request_id, reservation_no, room_id, user_id, title, start_time, end_time, participant_count, status, remark, cancel_reason) VALUES
(29, 'seed-extra-rsv-29', 'RSV20260917029', 9,  3, 'Department annual review',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '13:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '15:00:00'),
   40, 'CONFIRMED', 'Approved large-room event', NULL),
(30, 'seed-extra-rsv-30', 'RSV20260917030', 10, 2, 'Product launch rehearsal',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 4 DAY), '14:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 4 DAY), '16:00:00'),
   25, 'REJECTED', 'Waiting for a different venue', NULL),
(31, 'seed-extra-rsv-31', 'RSV20260917031', 11, 3, 'Study group discussion',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 5 DAY), '10:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 5 DAY), '12:00:00'),
   10, 'CONFIRMED', 'Regular discussion', NULL),
(32, 'seed-extra-rsv-32', 'RSV20260917032', 8,  4, 'Administrator coordination meeting',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 6 DAY), '15:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 6 DAY), '16:00:00'),
   12, 'CONFIRMED', 'Internal administration meeting', NULL),
(33, 'seed-extra-rsv-33', 'RSV20260917033', 7,  2, 'Team planning session',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '09:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '10:00:00'),
   6, 'CANCELLED', NULL, 'Schedule changed by the organizer'),
(34, 'seed-extra-rsv-34', 'RSV20260917034', 8,  3, 'Cross-team sync',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '14:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '16:00:00'),
   12, 'CONFIRMED', 'Weekly cross-team sync', NULL),
(35, 'seed-extra-rsv-35', 'RSV20260917035', 9,  4, 'Public lecture preparation',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 7 DAY), '10:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 7 DAY), '12:00:00'),
   30, 'PENDING', 'Awaiting administrator approval', NULL);

INSERT INTO approval_record (id, reservation_id, approver_id, action, remark) VALUES
(1, 3, 1, 'APPROVE', '场地与设备已确认，同意使用'),
(2, 4, 1, 'REJECT',  '该时段需预留场地维护，建议改期至其他日期');

-- =============================================================================
-- 7. 操作日志：覆盖审批、驳回、建会议室、改分类、强制取消五类关键管理行为
-- =============================================================================
INSERT INTO approval_record (id, reservation_id, approver_id, action, remark) VALUES
(7, 29, 4, 'APPROVE', 'Approved by lzx for the large-room event'),
(8, 30, 4, 'REJECT',  'The special room is reserved for another event');

INSERT INTO operation_log (id, user_id, operation_type, business_type, business_id, content, ip_address) VALUES
(1, 1, 'CREATE_ROOM',              'MEETING_ROOM',  5, '新增会议室 B502（阶梯报告厅，大型会议室分类）',        '127.0.0.1'),
(2, 1, 'UPDATE_CATEGORY',          'ROOM_CATEGORY', 4, '调整特殊会议室规则：需审批，单次最长8小时',            '127.0.0.1'),
(3, 1, 'APPROVE_RESERVATION',      'RESERVATION',   3, '审批通过预约 RSV20260901003（新产品内部路演）',        '127.0.0.1'),
(4, 1, 'REJECT_RESERVATION',       'RESERVATION',   4, '驳回预约 RSV20260901004（社团招新宣讲）',              '127.0.0.1'),
(5, 1, 'FORCE_CANCEL_RESERVATION', 'RESERVATION',   5, '强制取消预约 RSV20260901005（小组讨论），原因：设备检修', '127.0.0.1');

-- =============================================================================
-- 8. 违规与信用记录（v1.3 身份治理）：与第 1 节信用分保持一致
--    zhangsan 100+10=110；lisi 100-20=80（仍高于预约资格门槛 60，可正常预约）
-- =============================================================================
INSERT INTO operation_log (id, user_id, operation_type, business_type, business_id, content, ip_address) VALUES
(11, 4, 'CREATE_ROOM',         'MEETING_ROOM', 7,  'Created meeting room C101', '127.0.0.1'),
(12, 4, 'CREATE_ROOM',         'MEETING_ROOM', 8,  'Created meeting room C201', '127.0.0.1'),
(13, 4, 'CREATE_ROOM',         'MEETING_ROOM', 9,  'Created meeting room D301', '127.0.0.1'),
(14, 4, 'CREATE_ROOM',         'MEETING_ROOM', 10, 'Created meeting room D401', '127.0.0.1'),
(15, 4, 'APPROVE_RESERVATION', 'RESERVATION',  29, 'Approved reservation RSV20260917029', '127.0.0.1'),
(16, 4, 'REJECT_RESERVATION',  'RESERVATION',  30, 'Rejected reservation RSV20260917030', '127.0.0.1');

INSERT INTO user_violation (id, user_id, violation_type, credit_change, reason, operator_id) VALUES
(1, 2, 'CREDIT_REWARD', 10,  '协助保障多场大型会议顺利举行，信用奖励', 1),
(2, 3, 'CREDIT_DEDUCT', -20, '预约后未到场且未提前取消，信用扣分',     1);
