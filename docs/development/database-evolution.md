# Database Evolution Policy v0.2

## 1. 当前版本

`Database Schema v1.4` 是当前目标快照（v1.1 Team-Ready Baseline + v1.4 资源管理增量）。`sql/schema.sql` 必须始终表示从零初始化后的最新完整结构；增量变化必须保存在 `sql/migrations/`。

核心表继续保留：

```text
sys_user
room_category
meeting_room
room_facility
room_open_rule
reservation
approval_record
operation_log
```

v1.1 新增最小开放时间表 `room_open_rule`；v1.4 新增资源域轻量表 `room_maintenance`（维护计划）与 `facility_repair_ticket`（设施报修工单），不提前创建参会人、通知、统计等未实现功能的空表。

## 2. Migration 规则

- 文件名使用 `V<major>_<minor>__<short_description>.sql`，例如 `V1_1__team_ready_baseline.sql`；
- 每个业务域新增表、列、索引或约束都必须独立 migration，并在 PR 描述中说明 owner；
- 禁止五名开发者直接编辑 `schema.sql` 而不提供 migration；
- `schema.sql` 是完整快照，不是替代 migration 的临时草稿；
- migration 必须可重复审阅，注明执行前提、数据回填、回滚风险和影响范围；
- 线上/共享数据库执行顺序必须可追踪，不能依赖手工修改。

## 3. v1.1 变更

`V1_1__team_ready_baseline.sql` 至少包含：

1. `reservation.request_id` 非空字段；
2. `uk_reservation_request_id` 唯一约束；
3. `room_open_rule(id, room_id, weekday, open_time, close_time, enabled)`；
4. 预约状态数据清理到 `PENDING/CONFIRMED/REJECTED/CANCELLED`；
5. 旧明文种子密码替换为 BCrypt（种子脚本可重建数据库，因此直接替换）；
6. 相关索引和外键。

## 4. v1.4 变更

`V1_4__resource_management.sql`（Owner: resource）包含：

1. `room_maintenance(id, room_id, reason, start_time, end_time, status, created_by, ...)`：维护计划登记表，状态 `PLANNED/FINISHED`；
2. `facility_repair_ticket(id, room_id, facility_id, facility_name, issue, status, reporter_id, reporter_name, resolved_at, resolve_remark)`：报修工单表，状态 `OPEN/RESOLVED`，`facility_name` 为报修时快照；
3. 前置条件为 v1.1 基线，不改动既有表；
4. 同步更新 `schema.sql` 完整快照到 v1.4。

## 5. 数据所有权

每个 migration 必须标注 Domain owner：

- identity：`sys_user`；
- resource：`meeting_room`、`room_category`、`room_facility`、`room_open_rule`、`room_maintenance`、`facility_repair_ticket`；
- reservation：`reservation`；
- administration：`approval_record`、`operation_log`；
- meeting：后续新增表。

跨域变更应在同一 PR 中提交契约影响说明，但不能由一个 Domain 直接修改另一个 Domain 的 Mapper。

