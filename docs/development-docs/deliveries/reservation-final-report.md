# Reservation Domain 最终交付报告

- **分支**：`feature/reservation-core`
- **负责人**：李宗熹（2413042129，组长，reservation domain）
- **基线**：Team-Ready Baseline v0.2（`7649410 docs(require):文档更新`）
- **交付日期**：2026-09-15

## 1. 交付范围总览

| Phase | 内容 | 状态 |
| --- | --- | --- |
| A | requestId 用户作用域幂等 | ✅ 已完成（migration + 三处查询 + 测试） |
| A | 普通 cancel 仅限预约本人 | ✅ 已完成（ADMIN 改走 lifecycle.forceCancel） |
| A | 禁止跨日预约 | ✅ 已完成（18:00→次日 01:00 有测试） |
| A | ReservationLifecycleService | ✅ 已完成（approve/reject/forceCancel） |
| B | 预约详情 / 本人预约 / calendar | ✅ 详情新增，后两者基线已有并保留 |
| B | 预约修改/改期（重走全规则、排除自身） | ✅ 已完成 |
| B | 空闲时段与可用房间（reservation 侧） | ✅ 占用公开查询服务已交付 |
| C | 临时延长 / 周期预约 / 候补队列 | ⏳ 未开始，按任务书待基础稳定后独立 commit |

## 2. 数据库变更（唯一 migration）

文件：`sql/migrations/V1_2__reservation_hardening.sql`（owner: reservation）

1. 删除全局幂等键 `uk_reservation_request_id(request_id)`；
2. 新增用户作用域幂等键 `uk_reservation_user_request(user_id, request_id)`；
3. 不修改任何列与数据，历史数据天然满足新约束；回滚为对称 ALTER。

- **升级前置版本**：v1.1（`V1_1__team_ready_baseline.sql` 已执行）。
- **验证方式**：`SHOW INDEX FROM reservation WHERE Key_name='uk_reservation_user_request';`
- 本地 meeting_room 库已应用并验证通过。
- `sql/schema.sql` 按并行开发约束未动，待组长集成阶段统一同步快照。

## 3. API 变更

| Method | Path | 变更 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/reservations` | 语义修正 | 幂等键收敛到 `(userId, requestId)`；新增跨日校验 400 |
| POST | `/api/reservations/{id}/cancel` | 权限收紧 | 仅预约本人；ADMIN 走 administration 的强制取消（reason + audit） |
| GET | `/api/reservations/{id}` | 新增 | 详情；本人可看，ADMIN 只读可见 |
| PUT | `/api/reservations/{id}` | 新增 | 改期/修改（roomId/title/时间/人数/remark），仅本人、仅 PENDING/CONFIRMED、未开始；重走行锁 + 资源规则 + 半开区间冲突（排除自身 id） |

关键语义（与冻结契约一致，未改 `core-business-contract.md`）：

- 时间仍为 `[start, end)` 半开区间；冲突表达式 `newStart < oldEnd AND newEnd > oldStart`；
- `PENDING/CONFIRMED` 占用时间参与冲突，`REJECTED/CANCELLED` 不占用；
- 单日开放模型：`startDate != endDate` 直接 `400 VALIDATION_ERROR`；
- 改期后状态按新分类 `approval_required` 重推导（改入受控会议室 → 回到 `PENDING`）。

## 4. 跨域协作入口（其他 Domain 必读）

1. **administration**：审批/强制取消请调用 `ReservationLifecycleService`：
   - `approve(id, operatorId)`：仅 `PENDING → CONFIRMED`；
   - `reject(id, operatorId, reason)`：仅 `PENDING → REJECTED`，reason 必填；
   - `forceCancel(id, operatorId, reason)`：`PENDING/CONFIRMED → CANCELLED`，reason 必填，已开始会议亦可强制取消；
   - 任何 Domain **禁止**直接调用 `ReservationMapper.updateStatus()`；审批记录/操作日志仍由 administration 在调用前后自行落库。
2. **resource**：空闲时段/可用房间组合请使用 `ReservationOccupancyService`：
   - `findActiveOccupancy(roomId, start, end)`：返回 `[start,end)` 内 `PENDING/CONFIRMED` 占用切片；
   - `isOccupied(roomId, start, end)`：半开区间重叠判定；
   - 禁止直接查询 reservation 表。

## 5. 测试证据

单元测试（Mockito，无 DB 依赖，CI 可跑）：

```text
mvn clean test
Tests run: 42, Failures: 0, Errors: 0, Skipped: 0
```

覆盖：幂等命中与用户隔离、锁后重查、DuplicateKey 回查、本人/他人/ADMIN 取消权限、跨日拒绝（18:00→次日01:00）、
生命周期状态机（approve/reject/forceCancel 非法迁移拒绝、reason 必填）、改期全规则重走与排除自身、详情权限、占用查询。

集成测试（`ReservationConcurrencyIT`，需真实 MySQL，`TIMESLOT_IT_DB=true` 启用，CI 自动跳过）：

```text
DB_PASSWORD=*** TIMESLOT_IT_DB=true mvn test -Dtest=ReservationConcurrencyIT
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
```

1. **10 并发同室同时段：恰好 1 个创建成功，其余 9 个 `RESERVATION_TIME_CONFLICT`** ✅
2. 同用户同 requestId 幂等命中；不同用户同 requestId 独立创建、不串数据 ✅
3. `PENDING` 参与冲突判定（受控会议室第二个预约被拒）✅
4. 改期撞他人预约被拒；原地改期（排除自身 id）成功 ✅
5. 不同会议室并发互不阻塞（各恰好 1 个成功）✅

## 6. CI

新增 `.github/workflows/ci.yml`（`ci:` 提交）：push/PR 到 main 时，frontend 执行 `npm ci / type-check / build`，
backend 使用 Temurin Java 21 执行 `mvn -B clean test`（集成测试默认跳过，不依赖数据库）。

## 7. 提交清单（Conventional Commits）

```text
95a308e fix(reservation): 将幂等键收敛到用户作用域
5624ad7 fix(reservation): 限制取消操作仅允许预约本人
6e6aa9e fix(reservation): 禁止跨日期预约
81a20e9 feat(reservation): 增加预约生命周期公开服务
5f4da37 feat(reservation): 实现预约详情与改期
f1a361f feat(reservation): 增加空闲时段占用公开查询
70642dc ci: 增加前后端基础校验流水线
9641941 test(reservation): 扩展并发幂等与生命周期集成测试
docs(reservation): 更新预约核心规则与集成说明（本报告）
```

## 8. 边界与合规自查

- 未直接访问其他 Domain 的 Mapper / 数据表；resource 只通过 `ResourceQueryService` 公开方法调用；
- 未修改历史 migration（仅新增 V1_2）、未重写 `sql/schema.sql`、未改冻结契约；
- 新 API 均返回 `ApiResponse<T>`，错误码沿用全局枚举，未新增错误码；
- 后端接口为最终授权依据；前端按钮显隐仅 UX。

## 9. 留给集成阶段（组长）的事项

1. 五个 PR 齐后统一同步 `sql/schema.sql` 至 v1.2 快照，并核对各域 migration 顺序；
2. administration PR 接入 `ReservationLifecycleService` 后，删除/废弃其内部直接状态更新路径；
3. 前端需同步：ADMIN 侧“取消他人预约”入口迁移到强制取消（必填原因）；新增详情/改期入口；
4. 建议保留 100 并发额外验证（本轮已验证 10 并发；机器本地可扩展至 100 线程重跑同一测试）；
5. Phase C（临时延长/周期/候补）使用 `V1_9__reservation_recurrence_waitlist.sql` 或更高，不占用已发布的 V1_1~V1_8。
