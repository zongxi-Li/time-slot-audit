# TimeSlot Team-Ready Baseline v0.2 最终报告

日期：2026-09-12  
最终评级：**TEAM READY**

## 1. 原始仓库状态

原仓库是根目录 Vue 3 + Vite + Pinia Mock Demo，尚无 Spring Boot 后端。预约、会议室和管理员页面主要读取前端 Mock/内存 Store；数据库是 7 张核心表的 v1.0 原型，缺少 `request_id` 幂等约束和会议室开放时间规则；状态、角色和会议室状态在数据库、前端和需求文档之间存在漂移。

Phase 0 审计详见 [team-ready-baseline-v0.2-audit.md](team-ready-baseline-v0.2-audit.md)。原有两份 Draw.io 文件在任务开始时已有用户修改，本次保留未主动修改。

## 2. 实际修改范围

- 将 Vue 工程迁移到 `frontend/`，保留既有页面、日历组件和视觉样式。
- 新增 `backend/` Spring Boot 3.3.5 + Maven 模块，编译目标固定为 Java 21。
- 新增 `sql/migrations/V1_1__team_ready_baseline.sql`，并同步维护 `sql/schema.sql`、`sql/data.sql`。
- 新增业务契约、域边界、数据库演进、API 合约和五人协作规范文档。
- 新增真实 API Client、Mock/API 双模式、登录页和真实 API 路由保护。
- 新增预约时间区间单元测试、预约 Service 业务测试和可重复执行的并发脚本。
- 未自动创建或 push 远程分支，未 merge main，未删除 Mock Demo。

## 3. 架构变化

后端采用模块化单体，包边界为：

```text
com.timeslot
├── common
├── identity
├── resource
├── reservation
├── meeting
└── administration
```

`common` 仅包含统一响应、异常、安全上下文和配置。`reservation` 拥有预约生命周期；`resource` 拥有会议室、类别、设施和开放规则；`identity` 拥有用户；`administration` 预留审批记录、操作日志和统计读取模型；`meeting` 预留会议执行阶段能力。详细边界见 [domain-boundaries.md](domain-boundaries.md)。

前端新增：

```text
frontend/src/
├── modules/{identity,resource,reservation,meeting,administration}
├── shared/api
├── shared/types
├── router
├── layouts
└── stores
```

原页面暂不大规模搬迁，避免破坏现有 Demo；本轮真实链路优先接入 identity、resource、reservation。

## 4. 数据库变化

Schema v1.1 保留原 7 张核心表，并完成：

- `reservation.request_id VARCHAR(100) NOT NULL` 及唯一约束 `uk_reservation_request_id`。
- 新增 `room_open_rule(id, room_id, weekday, open_time, close_time, enabled)`。
- 预约持久化状态收敛为 `PENDING/CONFIRMED/REJECTED/CANCELLED`。
- 演示用户密码改为 BCrypt；测试密码仍为文档约定的 `123456`。
- 历史 `COMPLETED` 种子预约改为 `CONFIRMED`，由展示层按时间动态推导已完成状态。
- 规定未来所有业务域新增表必须使用独立 migration，并同步更新完整快照 `schema.sql`。

数据库演进规则见 [database-evolution.md](database-evolution.md)。

## 5. 状态模型最终定义

预约持久化状态唯一为：

`PENDING`、`CONFIRMED`、`REJECTED`、`CANCELLED`。

`PENDING` 和 `CONFIRMED` 参与占用判断；`REJECTED` 和 `CANCELLED` 不参与。展示状态 `UPCOMING/IN_USE/COMPLETED` 由当前时间、预约时间和持久化状态动态推导，不写回数据库。

时间区间统一为半开区间 `[start_time, end_time)`，冲突条件为：

```text
existing.start_time < newEnd
AND existing.end_time > newStart
```

会议室 API 状态唯一为 `AVAILABLE/MAINTENANCE/DISABLED`；角色基线为 `USER/ADMIN`。完整冻结契约见 [core-business-contract.md](core-business-contract.md)。

## 6. API 最终定义

已实现代码路径：

- `POST /api/auth/login`
- `GET /api/users/me`
- `GET /api/rooms`
- `GET /api/reservations/calendar`
- `GET /api/reservations/my`
- `POST /api/reservations`
- `POST /api/reservations/{id}/cancel`

统一响应为 `ApiResponse<T>`，错误码包括 `VALIDATION_ERROR`、`UNAUTHORIZED`、`FORBIDDEN`、`RESOURCE_NOT_FOUND`、`ROOM_UNAVAILABLE`、`ROOM_CAPACITY_EXCEEDED`、`RESERVATION_TIME_CONFLICT`、`RESERVATION_INVALID_STATE`、`DUPLICATE_REQUEST` 和 `INTERNAL_ERROR`。

接口详情和 IMPLEMENTED/PLANNED 标记见 [api-contract-v0.2.md](api-contract-v0.2.md)。

## 7. 并发控制真实实现

`ReservationService.createReservation()` 使用事务，并按以下顺序执行：

1. 获取当前用户并校验请求。
2. 预查 `request_id`。
3. 校验时间、会议室状态、类别规则、容量和开放时间。
4. 通过 `SELECT ... FROM meeting_room WHERE id = ? FOR UPDATE` 锁定单个会议室行。
5. 在持有会议室行锁后查询 `PENDING/CONFIRMED` 的半开区间冲突。
6. 根据 `approval_required` 写入 `PENDING` 或 `CONFIRMED`。
7. 提交事务。

创建事务明确使用 `READ_COMMITTED`，避免请求在等待会议室行锁前建立的旧快照遮蔽前一个事务刚提交的冲突预约。代码没有使用全局 `synchronized` 或 Java 全局锁；不同会议室使用不同的行锁粒度。时间冲突和 Service 测试均已覆盖。

本机 100 请求实测结果为同一会议室 `201: 1`、`409: 99`，calendar 返回 1 条有效区间且无重叠；两个不同会议室并行 200 请求时各自均为 `201: 1`、`409: 99`，验证没有全局锁阻塞。

## 8. 幂等控制真实实现

创建预约要求 `requestId`。应用层先查询已有请求，数据库唯一约束作为并发最终防线；发生重复键时，Service 查询并返回已有预约，不将正常重试转换为 500。

## 9. 前端 Mock/API 双模式

- `VITE_USE_MOCK=true`：保留原有 Demo Mock 和 Pinia 行为。
- `VITE_USE_MOCK=false`：默认模式，统一经 `src/shared/api/` 调用 Spring Boot。
- Vite 将 `/api` 代理到 `http://localhost:8080`。
- JWT 统一由 API Client 注入；401 清理 token 并回到登录流程。
- Vite API 代理默认指向 `http://localhost:8080`，可用 `VITE_API_PROXY_TARGET` 临时覆盖到其他本地后端端口。
- 预约冲突收到真实 409 时，保留标题、人数和备注，提示服务端错误并刷新相关日历，不刷新整页。
- 前端 `isTimeOverlap()` 只作为即时 UX 预校验，服务端事务裁决优先。

## 10. 测试结果

已执行：

- `cd frontend && npm install`：通过。
- `cd frontend && npm run type-check`：通过。
- `cd frontend && npm run build`：通过；仅有 Vite 大 chunk 提示。
- `cd backend && mvn clean test`：通过，15 tests，0 failures，0 errors。
- 时间区间测试：完全相同、左右重叠、包含/被包含、首尾相接、完全分离共 7 类。
- Reservation Service 测试：正常创建、审批型 PENDING、冲突、维护、容量、非法时间、重复 requestId，以及持锁后的并发重试回查共 8 类。
- `scripts/concurrency-test.py`：已实际执行 100 请求同会议室竞争，以及两个 room 各 100 请求的并行竞争；两组均通过。
- Playwright 真实浏览器：登录页使用 `zhangsan/123456` 登录后进入预约看板，真实加载 6 间 MySQL 会议室；修复东八区日历结束日期问题后，calendar 请求返回 200。

## 11. 运行与构建结果

本机环境：

- Temurin OpenJDK `21.0.11 LTS`。
- Maven `3.9.12`。
- MySQL Community Server `8.0.43`，Windows 服务 `MySQL80` 正在运行。

已使用用户提供的 SQL 密码完成 MySQL 连接验证，并通过原始 UTF-8 文件重定向执行 `schema.sql`、`data.sql`。初始化后校验为 3 users、6 rooms、6 reservations、42 open rules。密码未写入仓库文件。

Spring Boot 已在临时端口 `18080` 成功启动并连接 MySQL。实测结果：登录 HTTP 200、`/users/me` 返回 `zhangsan/USER`、`/rooms` 返回 6 间会议室、calendar 正常读取；普通会议室创建返回 `CONFIRMED`，重复 requestId 返回相同预约 ID，冲突返回 HTTP 409 `RESERVATION_TIME_CONFLICT`，取消后 calendar 不再返回该预约，审批型会议室创建返回 `PENDING`。

测试生成的 `live-*` 和 `concurrency-*` 预约已清理，数据库恢复为 6 条种子预约。

## 12. 未实现功能

- 未实现完整会议室 CRUD、用户管理、完整审批管理和真实 Dashboard 统计。
- 未建设复杂 RBAC、节假日日历、通知、签到、Redis、MQ、微服务或工作流引擎。
- 管理员页面仍保留部分 Mock 行为，已通过文档和目录边界为后续开发预留位置。
- 上述功能属于后续 Domain 迭代，不阻塞本轮已冻结的 Team-Ready Baseline Gate。

## 13. 五人后续开发起点

| 成员 | Domain | 推荐起点 |
|---|---|---|
| 1 | `reservation` | `ReservationService`、审批生命周期、预约查询扩展和集成测试 |
| 2 | `resource` | 会议室/类别/设施 CRUD、开放时间规则扩展和 migration |
| 3 | `identity` | 用户资料、资格数据和真实管理员用户管理 |
| 4 | `meeting` | 参会人、签到、通知和执行阶段数据 |
| 5 | `administration` | `approval_record`、`operation_log`、统计读取模型和审计接口 |

协作约束见 [team-development-guide.md](team-development-guide.md)：每个 PR 必须说明 Domain 范围、API 变化、SQL migration、基本测试和构建结果。

## 14. 当前遗留风险

1. 当前未接入 Flyway/Liquibase；migration 以受审查的 SQL 文件管理，后续应在团队决定后统一迁移执行工具。
2. MySQL 真实版本、时区和连接配置仍需在共享开发环境中复核。
3. 需要补充 Testcontainers 或共享 MySQL 集成测试，把当前可重复实机脚本进一步纳入 CI。
4. Vite 构建存在大 chunk 警告，但不影响本轮 type-check/build 通过。
5. 管理员审批 API 和完整管理 CRUD 尚未实现，后续开发必须遵守已冻结的域边界。

## 15. Team-Ready Gate 表

| Gate | 结果 | 证据/说明 |
|---|---|---|
| G1 业务状态语义统一 | PASS | 核心契约、SQL、Java、Vue 已收敛 |
| G2 Reservation Status 唯一 | PASS | 四个持久化状态 |
| G3 MeetingRoom Status 唯一 | PASS | 三个 API 枚举状态 |
| G4 Schema v1.1 | PASS | 快照和 migration 已提交 |
| G5 request_id 幂等 | PASS | 唯一约束、重复键回查及真实 MySQL 重试均已验证 |
| G6 room_open_rule | PASS | 表、查询和 Service 校验已实现 |
| G7 前后端分离 | PASS | `frontend/` 与 `backend/` 已建立 |
| G8 Spring Boot 启动 | PASS | 临时端口 18080 启动成功 |
| G9 连接 MySQL | PASS | MySQL 8.0.43，真实账号连接成功 |
| G10 真实登录 | PASS | HTTP 200，JWT 和 `/users/me` 已验证 |
| G11 真实 rooms | PASS | MySQL 返回 6 间会议室 |
| G12 真实 calendar | PASS | 真实日期范围查询返回成功 |
| G13 真实 create | PASS | 普通会议室真实写入并清理 |
| G14 真实 HTTP 409 | PASS | 实测 `RESERVATION_TIME_CONFLICT` |
| G15 会议室行锁 | PASS | 100 请求同 room 最终仅 1 条有效预约 |
| G16 无全局锁 | PASS | 两个 room 并行测试均独立通过 |
| G17 requestId 重试 | PASS | 重试返回相同预约 ID |
| G18 Vue 消费真实 API | PASS | 浏览器登录后看板加载真实 rooms/calendar |
| G19 frontend type-check | PASS | npm script 通过 |
| G20 frontend build | PASS | npm script 通过 |
| G21 backend test | PASS | 15 tests 通过 |
| G22 五个 Domain Package | PASS | 五域目录已建立 |
| G23 Domain Boundary | PASS | 文档已完成 |
| G24 API Contract | PASS | IMPLEMENTED/PLANNED 已区分 |
| G25 Team Development Guide | PASS | 文档已完成 |

## 16. 最终结论

本次已完成从 Vue Mock 原型到“可供多人并行开发的代码与契约基线”的主要收敛工作，Java 版本按建议固定为 **Java 21**。在用户提供 SQL 密码后，数据库初始化、真实 API、Vue 浏览器链路、冲突、幂等和并发 Gate 均已完成实机验收。

因此当前版本评级为：

> **TEAM READY**

当前版本可以作为五人并行开发的正式基线。后续成员应从各自 Domain 边界开始，通过独立 migration、API 变更说明和基本测试继续迭代；未实现的高级功能不应绕过本轮已冻结的公共契约。
