-- TimeSlot Database Schema v1.3 incremental migration
-- Owner: identity (张陈浩 2413042116)
-- Prerequisite: v1.2 migrations have been applied (V1_1__team_ready_baseline.sql 及后续成员的 V1_2).
-- Scope: 身份治理 —— 部门、用户资料扩展（信用分/限制期）、违规与信用记录。
-- 设计约定：不做 RBAC（不创建 role/permission/user_role 表）；
--           预约资格由「账号状态 + 信用分 + 限制截止时间」推导，不单独建资格表。

USE meeting_room;

-- 1. 部门表：最小组织模型，仅列表/新增/修改，无删除（有用户挂靠时不提供删除）
CREATE TABLE department (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    dept_name   VARCHAR(50)  NOT NULL                COMMENT '部门名称（唯一）',
    description VARCHAR(200) NULL                    COMMENT '部门说明',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_department_name (dept_name)
) ENGINE = InnoDB COMMENT = '部门表（最小组织模型）';

-- 2. sys_user 扩展：部门归属 + 预约资格推导字段
--    credit_score 默认 100（新用户视为正常）；
--    restricted_until 为空或已过期表示无限制，非空且在未来表示处于黑名单/限制期。
ALTER TABLE sys_user
    ADD COLUMN department_id     BIGINT   NULL AFTER role COMMENT '所属部门ID（可空）',
    ADD COLUMN credit_score      INT      NOT NULL DEFAULT 100 AFTER department_id COMMENT '信用分（预约资格门槛由应用层维护）',
    ADD COLUMN restricted_until  DATETIME NULL AFTER credit_score COMMENT '限制截止时间（非空且在未来=黑名单/限制期）';

ALTER TABLE sys_user
    ADD CONSTRAINT fk_user_department FOREIGN KEY (department_id) REFERENCES department (id);

-- 3. 用户违规与信用记录表：只增不改，仅记录事实（扣分、加黑、解除、人工调整、禁用）
--    operator_id 为空表示系统自动触发（如自动进入黑名单），人工操作必须记录操作人。
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

-- 4. 演示用种子部门（不强制给存量用户分配，由管理员在 UI 中调整）
INSERT INTO department (dept_name, description) VALUES
('信息中心', '校园信息化建设与运维部门'),
('软件学院', '软件工程专业教学单位'),
('后勤保障处', '场地与后勤保障部门');
