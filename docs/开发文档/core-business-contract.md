# TimeSlot Core Business Contract v0.2

状态：冻结  
生效版本：Team-Ready Baseline v0.2  
日期：2026-09-11

本文档是前端、后端、数据库和测试共同依赖的业务语义契约。若旧 README、旧 Mock、旧 SQL 注释与本文冲突，以本文为准；变更本文必须经过团队评审，并同步 API、migration、测试和前端类型。

## 1. Reservation 持久化状态

数据库和 API 只允许以下四个大写状态：

| 状态 | 含义 | 是否占用时间资源 |
| --- | --- | --- |
| `PENDING` | 特殊会议室待审批 | 是 |
| `CONFIRMED` | 预约正式成立 | 是 |
| `REJECTED` | 审批被拒绝 | 否 |
| `CANCELLED` | 原有效预约被取消 | 否 |

`UPCOMING`、`IN_USE`、`COMPLETED` 是展示状态，不得持续写入 `reservation.status`：

```text
CONFIRMED + now < start_time                         -> UPCOMING
CONFIRMED + start_time <= now < end_time             -> IN_USE
CONFIRMED + now >= end_time                          -> COMPLETED
PENDING                                                   -> PENDING
REJECTED                                                  -> REJECTED
CANCELLED                                                 -> CANCELLED
```

后端以统一 `Clock` 计算并返回 `displayStatus`；前端只负责映射文案，不能重新按浏览器时间推导、更不能把展示状态提交给 API。

## 2. 冲突占用集合

当前审批模型下，`PENDING` 和 `CONFIRMED` 都是有效占用。所有冲突查询必须使用：

```sql
status IN ('PENDING', 'CONFIRMED')
```

`REJECTED` 和 `CANCELLED` 保留历史，但不参与冲突判断。

## 3. 时间区间

预约使用半开区间 `[start_time, end_time)`，API 使用完整 datetime，例如：

```text
2026-09-15T10:00:00
2026-09-15T11:00:00
```

唯一冲突条件：

```text
newStart < oldEnd AND newEnd > oldStart
```

因此 `10:00-11:00` 与 `11:00-12:00` 不冲突。所有创建、修改、可用会议室查询和测试必须复用这一语义。

## 4. MeetingRoom 资源状态

API/Domain 层统一使用：

```text
AVAILABLE / MAINTENANCE / DISABLED
```

数据库当前可以继续使用数字存储以保持已有设计兼容，但只能由 Resource mapper/domain 完成映射，不能把 `0/1/2` 泄露给 API。

- `AVAILABLE`：允许在其他规则通过且无冲突时新建预约；
- `MAINTENANCE`：不接受新预约；已有预约由管理员处置；
- `DISABLED`：不可预约；
- `IDLE`、`IN_USE` 等是根据预约和当前时间推导的展示状态，不是资源状态。

## 5. Role

当前基线仅允许：

```text
USER / ADMIN
```

不新增 permission tree、role_permission、user_role 或其他复杂 RBAC 表。后端接口是最终授权依据，前端按钮显隐只提供 UX。

## 6. 审批规则

`room_category.approval_required = false`：通过完整校验和冲突检查后写入 `CONFIRMED`。  
`room_category.approval_required = true`：通过完整校验和冲突检查后写入 `PENDING`，并立即占用时间资源。

管理员操作：

```text
APPROVE: PENDING -> CONFIRMED
REJECT:  PENDING -> REJECTED
```

审批和驳回只允许发生在 `start_time` 之前；开始后的待审批记录不得再被批准为已进行或已完成预约，须由运营流程单独处置。管理员强制取消允许在预约进行中发生，但 `now >= end_time` 时必须拒绝，确保 `COMPLETED` 展示终态不被改写为 `CANCELLED`。

绝不使用 `PENDING -> CANCELLED` 表示审批驳回。取消是对原有效预约的生命周期操作，不是审批结果。

## 7. 创建预约不变量与顺序

`ReservationService.createReservation()` 必须在一个事务中按以下顺序执行：

1. 获取当前认证用户；
2. 校验 `requestId`；
3. 查询既有 `requestId`，命中则返回既有业务结果；
4. 校验 `startTime < endTime`；
5. 对目标 `meeting_room` 执行 `SELECT ... FOR UPDATE`；
6. 校验会议室存在且为 `AVAILABLE`；
7. 查询会议室分类规则；
8. 校验提前天数、最大时长和参与人数；
9. 校验目标日期的 `room_open_rule`；
10. 查询同一会议室 `PENDING/CONFIRMED` 的冲突预约；
11. 冲突条件使用本文第 3 节唯一表达式；
12. 有冲突则抛出 `RESERVATION_TIME_CONFLICT`；
13. 根据 `approval_required` 选择 `PENDING` 或 `CONFIRMED`；
14. 写入 reservation 并提交。

先查冲突再锁房间、先查冲突再插入、全局 synchronized 或 Java 全局锁都不符合本契约。

## 8. 幂等与并发修改

- 创建预约的 `requestId` 由一次逻辑提交生成；网络超时后的重试必须复用同一个值，直到客户端明确放弃该次提交。
- `reservation.version` 是乐观锁版本。所有预约写操作递增版本；`PUT /api/reservations/{id}` 必须携带读取到的 `version`，不匹配时返回 `RESERVATION_INVALID_STATE` 并要求刷新，禁止静默后写覆盖。

## 9. Ownership

- `identity`：用户、角色和认证上下文；
- `resource`：会议室、分类、设施、开放时间；
- `reservation`：预约记录状态和生命周期；
- `meeting`：参会人、签到、通知等预约执行阶段数据；
- `administration`：审批行为历史、操作日志和统计读取模型。

跨域只能调用对方公开 Service/DTO，不得直接调用对方 Mapper 修改对方拥有的表。

