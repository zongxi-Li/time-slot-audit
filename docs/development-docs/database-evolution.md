# Database Evolution Policy v0.2

## 1. 当前版本

`Database Schema v1.11` 是当前完整快照（已整合 v1.1 至 v1.11），共 **17 张业务表**。当前仓库采用初始化脚本交付数据库结构，不再保留或执行 `sql/migrations/` 增量脚本。

从零建库推荐执行一体化脚本 `sql/init.sql`：它包含完整结构与演示种子数据。`schema.sql` 与 `data.sql` 保留作为分步脚本，三者必须保持一致。

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

v1.1 新增最小开放时间表 `room_open_rule`；v1.4 新增资源域轻量表；v1.5 新增 `reservation_attendee` 与 `notification`；v1.6 增加运营查询索引；v1.7 限制四个持久化预约状态；v1.8 增加 `reservation.version` 乐观锁列；v1.11 新增与预约一对一的 `meeting_execution` 实际使用记录表。完整结构均已同步到快照。

## 2. 初始化脚本规则

- 新增或调整表、字段、索引和约束时，直接同步 `sql/init.sql`、`sql/schema.sql` 与 `sql/data.sql`。
- `sql/init.sql` 是推荐入口，会重建完整数据库并写入演示数据。
- `sql/schema.sql` 只负责结构，`sql/data.sql` 只负责种子数据。
- 三个初始化脚本必须保持结构、约束、索引和数据的一致性。
- 已有业务数据库不得直接执行 reset-style 初始化脚本，应先备份并在可重建数据库验证。

## 3. 历史结构演进（已合并到初始化脚本）

早期 v1.1 基线包含：

1. `reservation.request_id` 非空字段；
2. `uk_reservation_request_id` 唯一约束；
3. `room_open_rule(id, room_id, weekday, open_time, close_time, enabled)`；
4. 预约状态数据清理到 `PENDING/CONFIRMED/REJECTED/CANCELLED`；
5. 旧明文种子密码替换为 BCrypt（种子脚本可重建数据库，因此直接替换）；
6. 相关索引和外键。

## 4. v1.4 至 v1.11 历史变化（已合并到初始化脚本）

资源管理阶段包含：

1. `room_maintenance(id, room_id, reason, start_time, end_time, status, created_by, ...)`：维护计划登记表，状态 `PLANNED/FINISHED`；
2. `facility_repair_ticket(id, room_id, facility_id, facility_name, issue, status, reporter_id, reporter_name, resolved_at, resolve_remark)`：报修工单表，状态 `OPEN/RESOLVED`，`facility_name` 为报修时快照；
3. 前置条件为 v1.1 基线，不改动既有表；
4. v1.5 增加会议执行表，v1.6 增加运营索引，v1.7 收敛预约状态，v1.8 增加乐观锁版本。
5. v1.11 增加会议级实际开始、实际结束和实际参会人数记录。
6. 同步更新 `schema.sql` 完整快照到 v1.11。

## 5. 数据所有权

当前表按以下 Domain 归属维护：

- identity：`sys_user`；
- resource：`meeting_room`、`room_category`、`room_facility`、`room_open_rule`、`room_maintenance`、`facility_repair_ticket`；
- reservation：`reservation`；
- administration：`approval_record`、`operation_log`；
- meeting：后续新增表。

跨域变更应在同一 PR 中提交契约影响说明，但不能由一个 Domain 直接修改另一个 Domain 的 Mapper。

