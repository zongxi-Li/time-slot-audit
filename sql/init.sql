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
TRUNCATE TABLE notification;
TRUNCATE TABLE reservation_attendee;
TRUNCATE TABLE meeting_execution;
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
-- 1. 部门：最小组织模型演示数据（v1.3 身份治理）
-- =============================================================================
INSERT INTO department (id, dept_name, description) VALUES
(1, '信息中心',     '校园信息化建设与运维部门'),
(2, '软件学院',     '软件工程专业教学单位'),
(3, '后勤保障处',   '场地与后勤保障部门'),
(4, '教务处',       '教学运行与课程协调部门'),
(5, '图书馆',       '文献资源与学习空间管理'),
(6, '外国语学院',   '外语教学与交流单位');

-- =============================================================================
-- 2. 用户：2 名管理员 + 9 名普通用户
--    credit_score 与第 12 节的信用记录累计一致；用户 11 信用分低于门槛 60，
--    且处于限制期内，用于演示预约资格判定。
-- =============================================================================
INSERT INTO sys_user (id, username, password, real_name, email, phone, role, department_id, credit_score, status) VALUES
(1,  'admin',    '$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm', '系统管理员', 'admin@timeslot.demo',    '13800000001', 'ADMIN', 1, 100, 1),
(2,  'zhangsan', '$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm', '张三',       'zhangsan@timeslot.demo', '13800000002', 'USER',  2, 110, 1),
(3,  'lisi',     '$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm', '李四',       'lisi@timeslot.demo',     '13800000003', 'USER',  1, 80,  1),
(4,  'lzx',      '$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm', 'LZX',        'lzx@timeslot.demo',      '13800000004', 'ADMIN', 1, 100, 1),
(5,  'wangwu',   '$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm', '王五',       'wangwu@timeslot.demo',   '13800000005', 'USER',  2, 110, 1),
(6,  'zhaoliu',  '$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm', '赵六',       'zhaoliu@timeslot.demo',  '13800000006', 'USER',  3, 95,  1),
(7,  'sunqi',    '$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm', '孙七',       'sunqi@timeslot.demo',    '13800000007', 'USER',  4, 100, 1),
(8,  'zhouba',   '$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm', '周八',       'zhouba@timeslot.demo',   '13800000008', 'USER',  5, 70,  1),
(9,  'wujiu',    '$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm', '吴九',       'wujiu@timeslot.demo',    '13800000009', 'USER',  6, 100, 1),
(10, 'zhengshi', '$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm', '郑十',       'zhengshi@timeslot.demo', '13800000010', 'USER',  2, 60,  1),
(11, 'liushiyi', '$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm', '刘十一',     'liushiyi@timeslot.demo', '13800000011', 'USER',  3, 45,  1);

-- 用户 11：信用分不足且处于限制期（演示黑名单/限制期对预约资格的影响）
UPDATE sys_user SET restricted_until = DATE_ADD(NOW(), INTERVAL 10 DAY) WHERE id = 11;

-- =============================================================================
-- 3. 会议室分类：审批开关配置在分类上
--    小型/普通免审批（提交即 CONFIRMED），大型/特殊需审批（提交进 PENDING）
-- =============================================================================
INSERT INTO room_category (id, category_name, min_capacity, max_capacity, approval_required, max_duration_minutes, advance_days, description) VALUES
(1, '小型会议室', 3,   8,   0, 1440, 7,  '3-8人日常讨论，免审批，单次最长24小时，可提前7天预约'),
(2, '普通会议室', 9,   20,  0, 1440, 7,  '9-20人常规会议，免审批，单次最长24小时，可提前7天预约'),
(3, '大型会议室', 21,  100, 1, 1440, 14, '21人以上大型会议，需管理员审批，单次最长24小时，可提前14天预约'),
(4, '特殊会议室', 10,  60,  1, 1440, 14, '路演厅、多功能厅等受控场地，需管理员审批，单次最长24小时，可提前14天预约');

-- =============================================================================
-- 4. 会议室：16 间，容量均落在所属分类 [min_capacity, max_capacity] 区间内
--    状态覆盖 1-可用 / 0-维护中 / 2-停用，用于演示可预约性校验
-- =============================================================================
INSERT INTO meeting_room (id, category_id, room_name, location, capacity, status, description) VALUES
(1,  1, 'A301', '教学楼A栋3层', 8,  1, '4-8人圆桌讨论间，配白板'),
(2,  1, 'A302', '教学楼A栋3层', 6,  1, '6人小组讨论间'),
(3,  2, 'B201', '教学楼B栋2层', 16, 1, '常规投影会议室'),
(4,  2, 'B202', '教学楼B栋2层', 20, 1, '支持视频会议的常规会议室'),
(5,  3, 'B502', '教学楼B栋5层', 60, 1, '阶梯报告厅，大型会议场地'),
(6,  4, 'S101', '综合楼1层',    40, 1, '路演厅，含灯光音响，使用需审批'),
(7,  1, 'C101', '教学楼C栋1层', 8,  1, '小型团队讨论室，配投影仪与白板'),
(8,  2, 'C201', '教学楼C栋2层', 18, 1, '标准会议室，配视频会议设备'),
(9,  3, 'D301', '教学楼D栋3层', 50, 1, '大型阶梯教室，用于院系活动'),
(10, 4, 'D401', '教学楼D栋4层', 35, 1, '多功能报告厅'),
(11, 2, 'E201', '图书馆E栋2层', 16, 1, '安静型讨论室'),
(12, 2, 'E202', '图书馆E栋2层', 18, 1, '图书馆研讨室，配视频会议设备'),
(13, 1, 'A303', '教学楼A栋3层', 8,  1, '小型面试与谈话间'),
(14, 3, 'B503', '教学楼B栋5层', 80, 1, '大型多功能厅，支持舞台灯光与扩音'),
(15, 2, 'B203', '教学楼B栋2层', 20, 0, '维护中：更换投影设备，暂停预约'),
(16, 1, 'A304', '教学楼A栋3层', 8,  2, '已停用：改造为教学储物间');

-- =============================================================================
-- 5. 会议室设施：覆盖任务书"投影、视频会议、白板等"并扩充种类，
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
(34, 11, '无线投屏',    1, NULL),
(35, 12, '显示屏',      1, '75寸显示屏'),
(36, 12, '白板',        1, NULL),
(37, 12, '视频会议设备', 1, NULL),
(38, 13, '显示屏',      1, '面试用投屏'),
(39, 13, '白板',        1, NULL),
(40, 14, '投影仪',      2, NULL),
(41, 14, '音响系统',    1, '含调音台'),
(42, 14, '舞台灯光',    1, NULL),
(43, 14, '无线麦克风',  6, NULL),
(44, 14, '视频会议设备', 1, NULL),
(45, 15, '投影仪',      1, '待更换'),
(46, 15, '白板',        1, NULL),
(47, 16, '白板',        1, NULL);

-- =============================================================================
-- 6. 每间会议室每周开放时间：周一至周日 08:00-19:00（16 间 × 7 天 = 112 条）。
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
-- 7. 预约：47 条，覆盖四个持久化状态与多种时间形态
--    · id 1-6、29-35：原始种子（四个状态 + 审批历史 + 过去会议）
--    · id 7-12      ：每周项目例会，连续 6 周（周期性预约演示）
--    · id 13、14、45：跨天预约（结束时间落在次日）
--    · id 15-25     ：已结束的 CONFIRMED，供签到、No-Show 与实际使用记录演示
--    · id 26-28     ：未来 CONFIRMED
--    · id 36-39     ：受控会议室 PENDING，等待管理员审批
--    · id 40、41    ：受控会议室 REJECTED，含审批驳回历史
--    · id 42-44     ：CANCELLED（用户主动取消 + 管理员强制取消）
--    · id 46、47    ：会议室转入维护/停用前的历史预约
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
   15, 'CONFIRMED', NULL, NULL),
(7, 'seed-rsv-7', 'RSV20260901007', 1, 2, '每周项目例会（第 1 周）',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '14:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '15:00:00'),
   8, 'CONFIRMED', '周期性会议，连续 6 周', NULL),
(8, 'seed-rsv-8', 'RSV20260901008', 1, 2, '每周项目例会（第 2 周）',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 10 DAY), '14:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 10 DAY), '15:00:00'),
   8, 'CONFIRMED', '周期性会议，连续 6 周', NULL),
(9, 'seed-rsv-9', 'RSV20260901009', 1, 2, '每周项目例会（第 3 周）',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 17 DAY), '14:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 17 DAY), '15:00:00'),
   8, 'CONFIRMED', '周期性会议，连续 6 周', NULL),
(10, 'seed-rsv-10', 'RSV20260901010', 1, 2, '每周项目例会（第 4 周）',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 24 DAY), '14:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 24 DAY), '15:00:00'),
   8, 'CONFIRMED', '周期性会议，连续 6 周', NULL),
(11, 'seed-rsv-11', 'RSV20260901011', 1, 2, '每周项目例会（第 5 周）',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 31 DAY), '14:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 31 DAY), '15:00:00'),
   8, 'CONFIRMED', '周期性会议，连续 6 周', NULL),
(12, 'seed-rsv-12', 'RSV20260901012', 1, 2, '每周项目例会（第 6 周）',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 38 DAY), '14:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 38 DAY), '15:00:00'),
   8, 'CONFIRMED', '周期性会议，连续 6 周', NULL),
(13, 'seed-rsv-13', 'RSV20260901013', 14, 5, '跨天场地布置与彩排',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 4 DAY), '22:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 5 DAY), '01:00:00'),
   12, 'CONFIRMED', '跨天使用，结束时间落在次日', NULL),
(14, 'seed-rsv-14', 'RSV20260901014', 5, 6, '跨天系统割接演练',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 9 DAY), '23:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 10 DAY), '03:00:00'),
   30, 'PENDING', '跨天演练，需管理员审批后执行', NULL),
(15, 'seed-rsv-15', 'RSV20260901015', 1, 3, '小组讨论：迭代任务拆分',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '09:00:00'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '10:00:00'),
   6, 'CONFIRMED', NULL, NULL),
(16, 'seed-rsv-16', 'RSV20260901016', 3, 2, '需求评审会',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '14:00:00'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '15:30:00'),
   14, 'CONFIRMED', '评审通过后进入开发', NULL),
(17, 'seed-rsv-17', 'RSV20260901017', 4, 5, '合作方视频对接',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 3 DAY), '09:30:00'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 3 DAY), '10:30:00'),
   10, 'CONFIRMED', '需开启视频会议设备', NULL),
(18, 'seed-rsv-18', 'RSV20260901018', 5, 6, '全院教职工大会',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 4 DAY), '15:00:00'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 4 DAY), '17:00:00'),
   55, 'CONFIRMED', '需扩音与投影', NULL),
(19, 'seed-rsv-19', 'RSV20260901019', 8, 7, '新员工入职培训',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY), '09:00:00'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY), '11:00:00'),
   15, 'CONFIRMED', NULL, NULL),
(20, 'seed-rsv-20', 'RSV20260901020', 6, 2, '产品发布会彩排',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 6 DAY), '14:00:00'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 6 DAY), '16:00:00'),
   28, 'CONFIRMED', '需要灯光和音响保障', NULL),
(21, 'seed-rsv-21', 'RSV20260901021', 11, 8, '读书分享会',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 7 DAY), '16:00:00'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 7 DAY), '17:30:00'),
   12, 'CONFIRMED', NULL, NULL),
(22, 'seed-rsv-22', 'RSV20260901022', 2, 9, '小组周会',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 8 DAY), '11:00:00'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 8 DAY), '12:00:00'),
   5, 'CONFIRMED', NULL, NULL),
(23, 'seed-rsv-23', 'RSV20260901023', 9, 3, '学术讲座：分布式系统实践',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 9 DAY), '14:00:00'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 9 DAY), '16:00:00'),
   45, 'CONFIRMED', '需录播', NULL),
(24, 'seed-rsv-24', 'RSV20260901024', 10, 5, '创新项目路演',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 10 DAY), '10:00:00'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 10 DAY), '12:00:00'),
   30, 'CONFIRMED', '需要舞台灯光', NULL),
(25, 'seed-rsv-25', 'RSV20260901025', 7, 6, '一对一沟通',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '16:00:00'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '17:00:00'),
   3, 'CONFIRMED', NULL, NULL),
(26, 'seed-rsv-26', 'RSV20260901026', 12, 5, '图书馆研讨：课程设计',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '10:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '12:00:00'),
   12, 'CONFIRMED', '需要视频会议设备', NULL),
(27, 'seed-rsv-27', 'RSV20260901027', 13, 7, '面试：后端开发实习生',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '09:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '10:30:00'),
   4, 'CONFIRMED', NULL, NULL),
(28, 'seed-rsv-28', 'RSV20260901028', 3, 9, '跨部门协调会',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '16:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '17:00:00'),
   15, 'CONFIRMED', NULL, NULL),
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
   30, 'PENDING', 'Awaiting administrator approval', NULL),
(36, 'seed-rsv-36', 'RSV20260917036', 6, 5, '社团联合汇演',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 6 DAY), '19:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 6 DAY), '21:00:00'),
   38, 'PENDING', '需要舞台灯光与音响', NULL),
(37, 'seed-rsv-37', 'RSV20260917037', 9, 6, '研究生开题报告会',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 8 DAY), '09:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 8 DAY), '12:00:00'),
   40, 'PENDING', '需要录播与话筒', NULL),
(38, 'seed-rsv-38', 'RSV20260917038', 5, 10, '年度表彰大会',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 11 DAY), '14:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 11 DAY), '17:00:00'),
   50, 'PENDING', '需提前布置会场', NULL),
(39, 'seed-rsv-39', 'RSV20260917039', 14, 7, '迎新晚会彩排',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 12 DAY), '18:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 12 DAY), '21:00:00'),
   60, 'PENDING', '需舞台灯光与调音台', NULL),
(40, 'seed-rsv-40', 'RSV20260917040', 6, 8, '商业宣讲活动',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 5 DAY), '17:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 5 DAY), '19:00:00'),
   30, 'REJECTED', NULL, NULL),
(41, 'seed-rsv-41', 'RSV20260917041', 9, 9, '外部培训占用申请',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 6 DAY), '13:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 6 DAY), '15:00:00'),
   35, 'REJECTED', NULL, NULL),
(42, 'seed-rsv-42', 'RSV20260917042', 3, 5, '临时取消的评审会',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 4 DAY), '15:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 4 DAY), '16:00:00'),
   10, 'CANCELLED', NULL, '主讲人行程变更，用户主动取消'),
(43, 'seed-rsv-43', 'RSV20260917043', 12, 6, '改期后的研讨',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '14:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '15:00:00'),
   8, 'CANCELLED', NULL, '该时段安排设备检修，管理员强制取消'),
(44, 'seed-rsv-44', 'RSV20260917044', 2, 9, '取消的小组会',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 5 DAY), '09:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 5 DAY), '10:00:00'),
   4, 'CANCELLED', NULL, '参会人数不足，用户主动取消'),
(45, 'seed-rsv-45', 'RSV20260917045', 4, 7, '通宵调试保障',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '20:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '02:00:00'),
   8, 'CONFIRMED', '跨天使用，结束时间落在次日', NULL),
(46, 'seed-rsv-46', 'RSV20260917046', 15, 2, '维护前最后一次会议',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 6 DAY), '10:00:00'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 6 DAY), '11:00:00'),
   12, 'CONFIRMED', '该会议室随后进入维护状态', NULL),
(47, 'seed-rsv-47', 'RSV20260917047', 16, 3, '停用前的讨论会',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 12 DAY), '15:00:00'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 12 DAY), '16:00:00'),
   6, 'CONFIRMED', '该会议室随后停用', NULL);

-- =============================================================================
-- 8. 审批记录：仅受控分类（大型/特殊会议室）的预约被管理员处理时产生
--    待审批（PENDING）的预约尚无记录；免审批分类不产生任何审批记录。
-- =============================================================================
INSERT INTO approval_record (id, reservation_id, approver_id, action, remark) VALUES
(1,  3,  1, 'APPROVE', '场地与设备已确认，同意使用'),
(2,  4,  1, 'REJECT',  '该时段需预留场地维护，建议改期至其他日期'),
(3,  13, 1, 'APPROVE', '跨天彩排已确认，注意闭楼时间与用电安全'),
(4,  18, 4, 'APPROVE', '全院大会已备案，同意使用阶梯报告厅'),
(5,  20, 1, 'APPROVE', '彩排需要灯光音响，已协调设备管理员到场'),
(6,  23, 4, 'APPROVE', '学术讲座已审核，同意使用并安排录播'),
(7,  29, 4, 'APPROVE', 'Approved by lzx for the large-room event'),
(8,  30, 4, 'REJECT',  'The special room is reserved for another event'),
(9,  24, 1, 'APPROVE', '路演场地已确认，同意使用'),
(10, 40, 1, 'REJECT',  '商业宣讲不属于校内教学科研活动，不予通过'),
(11, 41, 4, 'REJECT',  '该时段已安排研究生答辩，建议改用其他场地');

-- =============================================================================
-- 9. 会议实际使用记录：仅对已结束的 CONFIRMED 预约登记（一对一）
--    实际结束时间均早于当前时间；未登记的已结束预约保留为空，体现真实使用情况。
-- =============================================================================
INSERT INTO meeting_execution (reservation_id, actual_start_time, actual_end_time, actual_attendee_count, recorded_by) VALUES
(6,  TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '10:05:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '11:20:00'), 14, 2),
(15, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '09:55:00'), 6,  3),
(16, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '14:10:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '15:35:00'), 13, 2),
(17, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 3 DAY), '09:35:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 3 DAY), '10:20:00'), 9,  5),
(18, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 4 DAY), '15:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 4 DAY), '16:40:00'), 52, 6),
(19, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY), '09:10:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY), '10:50:00'), 14, 1),
(20, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 6 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 6 DAY), '16:10:00'), 30, 2),
(22, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 8 DAY), '11:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 8 DAY), '11:45:00'), 5,  9),
(23, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 9 DAY), '14:05:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 9 DAY), '15:50:00'), 41, 3),
(25, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '16:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '16:30:00'), 3,  6);

-- =============================================================================
-- 10. 参会人与出勤：组织者以 ORGANIZER 行存在
--     已结束会议为 CHECKED_OUT / CHECKED_IN / NO_SHOW，未来会议为 EXPECTED
-- =============================================================================
INSERT INTO reservation_attendee (reservation_id, user_id, attendee_role, attendance_status, check_in_at, check_out_at) VALUES
-- 每周项目例会（未来，待签到）
(7,  2, 'ORGANIZER', 'EXPECTED', NULL, NULL),
(7,  3, 'ATTENDEE',  'EXPECTED', NULL, NULL),
(7,  5, 'ATTENDEE',  'EXPECTED', NULL, NULL),
(7,  6, 'ATTENDEE',  'EXPECTED', NULL, NULL),
(8,  2, 'ORGANIZER', 'EXPECTED', NULL, NULL),
(8,  3, 'ATTENDEE',  'EXPECTED', NULL, NULL),
(8,  5, 'ATTENDEE',  'EXPECTED', NULL, NULL),
-- 小组讨论：迭代任务拆分（已结束，含缺席）
(15, 3, 'ORGANIZER', 'CHECKED_OUT', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '08:55:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '09:58:00')),
(15, 2, 'ATTENDEE',  'CHECKED_OUT', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '08:58:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '09:58:00')),
(15, 9, 'ATTENDEE',  'NO_SHOW',     NULL, NULL),
-- 需求评审会（已结束）
(16, 2, 'ORGANIZER', 'CHECKED_OUT', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '14:05:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '15:33:00')),
(16, 5, 'ATTENDEE',  'CHECKED_OUT', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '14:06:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '15:33:00')),
(16, 7, 'ATTENDEE',  'CHECKED_OUT', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '14:12:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '15:33:00')),
(16, 9, 'ATTENDEE',  'NO_SHOW',     NULL, NULL),
-- 合作方视频对接（已结束）
(17, 5, 'ORGANIZER', 'CHECKED_OUT', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 3 DAY), '09:30:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 3 DAY), '10:18:00')),
(17, 6, 'ATTENDEE',  'CHECKED_OUT', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 3 DAY), '09:32:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 3 DAY), '10:18:00')),
-- 新员工入职培训（已结束，含签到未签退）
(19, 7, 'ORGANIZER', 'CHECKED_OUT', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY), '09:05:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY), '10:52:00')),
(19, 8, 'ATTENDEE',  'CHECKED_IN',  TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY), '09:06:00'), NULL),
(19, 9, 'ATTENDEE',  'NO_SHOW',     NULL, NULL),
-- 产品发布会彩排（已结束）
(20, 2, 'ORGANIZER', 'CHECKED_OUT', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 6 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 6 DAY), '16:08:00')),
(20, 6, 'ATTENDEE',  'CHECKED_OUT', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 6 DAY), '13:58:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 6 DAY), '16:08:00')),
(20, 8, 'ATTENDEE',  'CHECKED_OUT', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 6 DAY), '14:02:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 6 DAY), '16:08:00')),
-- 小组周会（已结束）
(22, 9, 'ORGANIZER', 'CHECKED_OUT', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 8 DAY), '11:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 8 DAY), '11:43:00')),
(22, 10, 'ATTENDEE', 'CHECKED_OUT', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 8 DAY), '11:01:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 8 DAY), '11:43:00')),
-- 学术讲座（已结束，含缺席）
(23, 3, 'ORGANIZER', 'CHECKED_OUT', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 9 DAY), '13:58:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 9 DAY), '15:48:00')),
(23, 5, 'ATTENDEE',  'NO_SHOW',     NULL, NULL),
(23, 6, 'ATTENDEE',  'CHECKED_OUT', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 9 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 9 DAY), '15:48:00')),
-- 一对一沟通（已结束）
(25, 6, 'ORGANIZER', 'CHECKED_OUT', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '16:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '16:28:00')),
(25, 7, 'ATTENDEE',  'CHECKED_OUT', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '16:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '16:28:00')),
-- 未来会议（待签到）
(26, 5, 'ORGANIZER', 'EXPECTED', NULL, NULL),
(26, 6, 'ATTENDEE',  'EXPECTED', NULL, NULL),
(26, 7, 'ATTENDEE',  'EXPECTED', NULL, NULL),
(26, 8, 'ATTENDEE',  'EXPECTED', NULL, NULL),
(27, 7, 'ORGANIZER', 'EXPECTED', NULL, NULL),
(27, 9, 'ATTENDEE',  'EXPECTED', NULL, NULL),
(27, 10, 'ATTENDEE', 'EXPECTED', NULL, NULL),
(28, 9, 'ORGANIZER', 'EXPECTED', NULL, NULL),
(28, 5, 'ATTENDEE',  'EXPECTED', NULL, NULL),
(28, 6, 'ATTENDEE',  'EXPECTED', NULL, NULL),
(34, 3, 'ORGANIZER', 'EXPECTED', NULL, NULL),
(34, 2, 'ATTENDEE',  'EXPECTED', NULL, NULL),
(34, 5, 'ATTENDEE',  'EXPECTED', NULL, NULL),
(45, 7, 'ORGANIZER', 'EXPECTED', NULL, NULL),
(45, 5, 'ATTENDEE',  'EXPECTED', NULL, NULL),
(45, 6, 'ATTENDEE',  'EXPECTED', NULL, NULL),
(45, 10, 'ATTENDEE', 'EXPECTED', NULL, NULL);

-- =============================================================================
-- 11. 站内通知：覆盖会议执行与预约生命周期两类事件
--     dedup_key 遵循应用的确定性幂等键约定（调度类）或业务事实键（动作类）
-- =============================================================================
INSERT INTO notification (user_id, type, title, content, reservation_id, dedup_key, is_read, read_at) VALUES
-- 预约创建成功
(2,  'RESERVATION_CREATED', '预约创建成功', '你预约的「每周项目例会（第 1 周）」已创建，时间：A301 三天后 14:00-15:00。', 7, 'RESERVATION_CREATED:7', 0, NULL),
(5,  'RESERVATION_CREATED', '预约创建成功', '你预约的「图书馆研讨：课程设计」已创建。', 26, 'RESERVATION_CREATED:26', 1, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
-- 待审批广播给管理员
(1,  'RESERVATION_PENDING_APPROVAL', '有待审批预约', '「跨天系统割接演练」等待审批（B502）。', 14, 'RESERVATION_PENDING_APPROVAL:14', 0, NULL),
(4,  'RESERVATION_PENDING_APPROVAL', '有待审批预约', '「跨天系统割接演练」等待审批（B502）。', 14, 'RESERVATION_PENDING_APPROVAL:14', 1, DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
(1,  'RESERVATION_PENDING_APPROVAL', '有待审批预约', '「社团联合汇演」等待审批（S101）。', 36, 'RESERVATION_PENDING_APPROVAL:36', 0, NULL),
(4,  'RESERVATION_PENDING_APPROVAL', '有待审批预约', '「社团联合汇演」等待审批（S101）。', 36, 'RESERVATION_PENDING_APPROVAL:36', 0, NULL),
(1,  'RESERVATION_PENDING_APPROVAL', '有待审批预约', '「研究生开题报告会」等待审批（D301）。', 37, 'RESERVATION_PENDING_APPROVAL:37', 0, NULL),
(4,  'RESERVATION_PENDING_APPROVAL', '有待审批预约', '「研究生开题报告会」等待审批（D301）。', 37, 'RESERVATION_PENDING_APPROVAL:37', 1, DATE_SUB(NOW(), INTERVAL 1 DAY)),
-- 审批结果
(5,  'RESERVATION_APPROVED', '预约已通过审批', '「跨天场地布置与彩排」已通过审批，请按预约时间使用场地。', 13, 'RESERVATION_APPROVED:13', 1, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(6,  'RESERVATION_APPROVED', '预约已通过审批', '「全院教职工大会」已通过审批。', 18, 'RESERVATION_APPROVED:18', 1, DATE_SUB(NOW(), INTERVAL 4 DAY)),
(8,  'RESERVATION_REJECTED', '预约被驳回', '「商业宣讲活动」未通过审批：商业宣讲不属于校内教学科研活动。', 40, 'RESERVATION_REJECTED:40', 0, NULL),
(9,  'RESERVATION_REJECTED', '预约被驳回', '「外部培训占用申请」未通过审批：该时段已安排研究生答辩。', 41, 'RESERVATION_REJECTED:41', 0, NULL),
-- 预约取消
(5,  'RESERVATION_CANCELLED', '预约已取消', '「临时取消的评审会」已取消，原因：主讲人行程变更。', 42, 'RESERVATION_CANCELLED:42', 1, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(6,  'RESERVATION_CANCELLED', '预约被管理员取消', '「改期后的研讨」已被管理员强制取消，原因：该时段安排设备检修。', 43, 'RESERVATION_CANCELLED:43', 0, NULL),
(9,  'RESERVATION_CANCELLED', '预约已取消', '「取消的小组会」已取消，原因：参会人数不足。', 44, 'RESERVATION_CANCELLED:44', 0, NULL),
-- 被加入会议
(6,  'ATTENDEE_ADDED', '被加入会议', '你被加入「图书馆研讨：课程设计」。', 26, 'ATTENDEE_ADDED:26:6', 0, NULL),
(7,  'ATTENDEE_ADDED', '被加入会议', '你被加入「图书馆研讨：课程设计」。', 26, 'ATTENDEE_ADDED:26:7', 0, NULL),
(8,  'ATTENDEE_ADDED', '被加入会议', '你被加入「图书馆研讨：课程设计」。', 26, 'ATTENDEE_ADDED:26:8', 1, DATE_SUB(NOW(), INTERVAL 20 HOUR)),
(5,  'ATTENDEE_ADDED', '被加入会议', '你被加入「跨部门协调会」。', 28, 'ATTENDEE_ADDED:28:5', 0, NULL),
(6,  'ATTENDEE_ADDED', '被加入会议', '你被加入「跨部门协调会」。', 28, 'ATTENDEE_ADDED:28:6', 0, NULL),
(5,  'ATTENDEE_ADDED', '被加入会议', '你被加入「通宵调试保障」。', 45, 'ATTENDEE_ADDED:45:5', 0, NULL),
(6,  'ATTENDEE_ADDED', '被加入会议', '你被加入「通宵调试保障」。', 45, 'ATTENDEE_ADDED:45:6', 0, NULL),
(10, 'ATTENDEE_ADDED', '被加入会议', '你被加入「通宵调试保障」。', 45, 'ATTENDEE_ADDED:45:10', 0, NULL),
-- 被移出会议
(8,  'ATTENDEE_REMOVED', '被移出会议', '你已被移出「图书馆研讨：课程设计」。', 26, 'ATTENDEE_REMOVED:26:8', 0, NULL),
-- 会议开始提醒
(2,  'MEETING_REMINDER', '会议即将开始', '「每周项目例会（第 1 周）」将在 30 分钟后开始（A301）。', 7, 'MEETING_REMINDER:7:2', 0, NULL),
(3,  'MEETING_REMINDER', '会议即将开始', '「每周项目例会（第 1 周）」将在 30 分钟后开始（A301）。', 7, 'MEETING_REMINDER:7:3', 0, NULL),
(5,  'MEETING_REMINDER', '会议即将开始', '「每周项目例会（第 1 周）」将在 30 分钟后开始（A301）。', 7, 'MEETING_REMINDER:7:5', 1, DATE_SUB(NOW(), INTERVAL 25 MINUTE)),
(6,  'MEETING_REMINDER', '会议即将开始', '「每周项目例会（第 1 周）」将在 30 分钟后开始（A301）。', 7, 'MEETING_REMINDER:7:6', 0, NULL),
(7,  'MEETING_REMINDER', '会议即将开始', '「面试：后端开发实习生」将在 30 分钟后开始（A303）。', 27, 'MEETING_REMINDER:27:7', 0, NULL),
-- 缺席判定
(9,  'NO_SHOW_MARKED', '缺席记录', '你在「小组讨论：迭代任务拆分」中未签到，已记为缺席。', 15, 'NO_SHOW_MARKED:15:9', 0, NULL),
(9,  'NO_SHOW_MARKED', '缺席记录', '你在「需求评审会」中未签到，已记为缺席。', 16, 'NO_SHOW_MARKED:16:9', 0, NULL),
(9,  'NO_SHOW_MARKED', '缺席记录', '你在「新员工入职培训」中未签到，已记为缺席。', 19, 'NO_SHOW_MARKED:19:9', 1, DATE_SUB(NOW(), INTERVAL 4 DAY)),
(5,  'NO_SHOW_MARKED', '缺席记录', '你在「学术讲座：分布式系统实践」中未签到，已记为缺席。', 23, 'NO_SHOW_MARKED:23:5', 1, DATE_SUB(NOW(), INTERVAL 8 DAY));

-- =============================================================================
-- 12. 操作日志：只增不改，记录审批、驳回、强制取消、建会议室与改分类等
--     关键管理行为，business_type 与 business_id 组成动态业务引用。
-- =============================================================================
INSERT INTO operation_log (id, user_id, operation_type, business_type, business_id, content, ip_address) VALUES
(1,  1, 'CREATE_ROOM',              'MEETING_ROOM',  5,  '新增会议室 B502（阶梯报告厅，大型会议室分类）',                    '127.0.0.1'),
(2,  1, 'UPDATE_CATEGORY',          'ROOM_CATEGORY', 4,  '调整特殊会议室规则：需审批，单次最长8小时',                        '127.0.0.1'),
(3,  1, 'APPROVE_RESERVATION',      'RESERVATION',   3,  '审批通过预约 RSV20260901003（新产品内部路演）',                    '127.0.0.1'),
(4,  1, 'REJECT_RESERVATION',       'RESERVATION',   4,  '驳回预约 RSV20260901004（社团招新宣讲）',                          '127.0.0.1'),
(5,  1, 'FORCE_CANCEL_RESERVATION', 'RESERVATION',   5,  '强制取消预约 RSV20260901005（小组讨论），原因：设备检修',           '127.0.0.1'),
(11, 4, 'CREATE_ROOM',              'MEETING_ROOM',  7,  '新增会议室 C101（小型团队讨论室）',                                '127.0.0.1'),
(12, 4, 'CREATE_ROOM',              'MEETING_ROOM',  8,  '新增会议室 C201（标准会议室）',                                    '127.0.0.1'),
(13, 4, 'CREATE_ROOM',              'MEETING_ROOM',  9,  '新增会议室 D301（大型阶梯教室）',                                  '127.0.0.1'),
(14, 4, 'CREATE_ROOM',              'MEETING_ROOM',  10, '新增会议室 D401（多功能报告厅）',                                  '127.0.0.1'),
(15, 4, 'APPROVE_RESERVATION',      'RESERVATION',   29, '审批通过预约 RSV20260917029（Department annual review）',           '127.0.0.1'),
(16, 4, 'REJECT_RESERVATION',       'RESERVATION',   30, '驳回预约 RSV20260917030（Product launch rehearsal）',              '127.0.0.1'),
(17, 1, 'CREATE_ROOM',              'MEETING_ROOM',  12, '新增会议室 E202（图书馆研讨室，配视频会议设备）',                   '127.0.0.1'),
(18, 1, 'CREATE_ROOM',              'MEETING_ROOM',  13, '新增会议室 A303（小型面试与谈话间）',                              '127.0.0.1'),
(19, 1, 'CREATE_ROOM',              'MEETING_ROOM',  14, '新增会议室 B503（大型多功能厅，支持舞台灯光与扩音）',               '127.0.0.1'),
(20, 1, 'UPDATE_ROOM',              'MEETING_ROOM',  15, '将会议室 B203 状态置为维护中：更换投影设备',                        '127.0.0.1'),
(21, 1, 'UPDATE_ROOM',              'MEETING_ROOM',  16, '将会议室 A304 状态置为停用：改造为教学储物间',                      '127.0.0.1'),
(22, 1, 'UPDATE_CATEGORY',          'ROOM_CATEGORY', 3,  '调整大型会议室规则：需管理员审批，可提前14天预约',                   '127.0.0.1'),
(23, 1, 'APPROVE_RESERVATION',      'RESERVATION',   13, '审批通过预约 RSV20260901013（跨天场地布置与彩排）',                 '127.0.0.1'),
(24, 4, 'APPROVE_RESERVATION',      'RESERVATION',   18, '审批通过预约 RSV20260901018（全院教职工大会）',                     '127.0.0.1'),
(25, 1, 'APPROVE_RESERVATION',      'RESERVATION',   20, '审批通过预约 RSV20260901020（产品发布会彩排）',                     '127.0.0.1'),
(26, 4, 'APPROVE_RESERVATION',      'RESERVATION',   23, '审批通过预约 RSV20260901023（学术讲座：分布式系统实践）',            '127.0.0.1'),
(27, 1, 'APPROVE_RESERVATION',      'RESERVATION',   24, '审批通过预约 RSV20260901024（创新项目路演）',                       '127.0.0.1'),
(28, 1, 'REJECT_RESERVATION',       'RESERVATION',   40, '驳回预约 RSV20260917040（商业宣讲活动），原因：非校内教学科研活动',  '127.0.0.1'),
(29, 4, 'REJECT_RESERVATION',       'RESERVATION',   41, '驳回预约 RSV20260917041（外部培训占用申请），原因：时段冲突',       '127.0.0.1'),
(30, 1, 'FORCE_CANCEL_RESERVATION', 'RESERVATION',   43, '强制取消预约 RSV20260917043（改期后的研讨），原因：设备检修',        '127.0.0.1');

-- =============================================================================
-- 13. 违规与信用记录：只增不改，与第 2 节的 credit_score 保持一致
--     zhangsan 100+10=110；lisi 100-20=80；wangwu 100+10=110；zhaoliu 100-5=95
--     zhouba 100-30=70；zhengshi 100-40=60；liushiyi 100-55=45 且进入限制期
--     sunqi 的禁用/启用成对出现，净变化为 0，信用分保持 100
-- =============================================================================
INSERT INTO user_violation (id, user_id, violation_type, credit_change, reason, operator_id) VALUES
(1,  2,  'CREDIT_REWARD',  10,  '协助保障多场大型会议顺利举行，信用奖励',                     1),
(2,  3,  'CREDIT_DEDUCT',  -20, '预约后未到场且未提前取消，信用扣分',                         1),
(3,  5,  'CREDIT_REWARD',  10,  '主动承担跨天彩排的场地协调工作，信用奖励',                   1),
(4,  6,  'CREDIT_DEDUCT',  -5,  '会议结束后未及时登记实际使用记录，信用扣分',                 4),
(5,  8,  'CREDIT_DEDUCT',  -30, '多次预约后缺席且未签到，信用扣分',                           1),
(6,  10, 'CREDIT_DEDUCT',  -40, '违规占用受控会议室且未按流程申请，信用扣分',                 1),
(7,  11, 'CREDIT_DEDUCT',  -55, '连续三次预约后缺席，信用扣分',                               1),
(8,  11, 'BLACKLIST_SET',  0,   '信用分低于预约门槛，自动进入限制期 10 天',                   NULL),
(9,  7,  'ACCOUNT_DISABLE', 0,  '多次提交冲突预约并占用他人时段，管理员临时禁用账号',         1),
(10, 7,  'ACCOUNT_ENABLE',  0,  '已确认整改，恢复账号使用',                                   1);
