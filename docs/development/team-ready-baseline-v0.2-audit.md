# TimeSlot Team-Ready Baseline v0.2 - Phase 0 审计报告

审计日期：2026-09-11  
审计范围：当前工作区 `C:\Users\LZX\Desktop\TimeSlot` 的真实源码、SQL、需求规格说明书与 Git 状态  
审计结论：**NOT READY**

## 1. 审计方法与证据边界

本报告以仓库实际文件和可读取的《会议室预约与时间冲突检查系统_软件需求规格说明书》PDF 为准，不以 README 的进度描述作为唯一依据。重点检查了：

- 根目录前端工程配置：`package.json`、`vite.config.ts`、`tsconfig.json`、`src/main.ts`、`src/router/index.ts`；
- 前端领域代码：`src/types`、`src/stores`、`src/mock`、`src/utils`、`src/components`、`src/views`、`src/layouts`；
- 数据库脚本：`sql/schema.sql`、`sql/data.sql`；
- 需求规格书中的业务规则、冲突与并发要求、接口约定、状态枚举和项目分工章节；
- Git 分支和工作区状态。

当前分支为 `main`，与 `origin/main` 同步。工作区已有两份 Draw.io 文件被修改但未提交：

- `docs/figure/TimeSlot_核心架构图.drawio`
- `docs/figure/TimeSlot_需求规格技术图.drawio`

这两份既有修改不属于本次基线收敛范围，后续应保持不覆盖、不回滚。

## 2. 当前前端架构

当前仓库根目录直接是 Vue 3 + TypeScript + Vite 单体前端：

- Vue `^3.5.0`、Vite `^6.0.0`、Pinia `^3.0.0`、Vue Router `^4.5.0`、Element Plus；
- `src/App.vue`、`src/layouts/MainLayout.vue`、`src/router/index.ts` 提供应用壳、路由和管理员路由守卫；
- `src/views` 中包含预约看板、我的预约、会议室、管理员 Dashboard/预约/会议室/监控页面；
- `src/components` 中包含日/周预约网格、预约卡片、详情和新建预约弹窗；
- `src/stores` 按页面业务划分为 `auth`、`meetingRoom`、`reservation`、`monitor`，但尚未按 identity/resource/reservation/meeting/administration 模块隔离；
- 无 `frontend/`、`backend/` 正式前后端分离目录；无 `shared/api`。

现有页面视觉和交互原型较完整，不能因目录重构而删除或重写日历组件。本轮应优先保留页面和 CSS，仅替换数据来源及公共类型适配层。

## 3. 当前 Mock 数据来源

Mock 数据来源明确存在两层：

1. `src/mock/meetingRooms.ts` 导出 `mockMeetingRooms`，包含 A101、A102、A103、B201、B202 五间演示会议室；
2. `src/mock/reservations.ts` 以当前日期为锚点动态生成预约；
3. `src/stores/meetingRoom.ts` 在 store 初始化时复制会议室 Mock；
4. `src/stores/reservation.ts` 在 store 初始化时复制预约 Mock；
5. 刷新页面后状态恢复，数据不进入 MySQL，也没有 HTTP 请求。

当前没有 `VITE_USE_MOCK` 开关，组件通过 Pinia store 直接获得内存数据，因此组件无法区分 Mock 和真实 API 模式，也没有可替换的 repository/service adapter。

## 4. 当前 Pinia 业务逻辑

`src/stores/reservation.ts` 目前承担了过多职责：

- 内存数据存储、本人预约筛选、按会议室和日期查询；
- 前端 `findConflicts` 冲突判断；
- 可用会议室推荐；
- 新增、取消、审批通过、审批驳回、强制取消；
- 当前用户来自 `auth` store 的前端角色切换。

其主要问题：

- `addReservation` 直接写入数组，没有后端事务、行锁、容量、开放时间、提前天数或最大时长校验；
- `findConflicts` 仅在前端内存执行，不能作为最终裁决；
- `rejectReservation` 将 `pending` 改成 `cancelled`，违反审批驳回必须进入 `REJECTED` 的冻结规则；
- `activeReservations` 仅排除 lowercase `cancelled`，没有统一占用集合契约；
- `monitor.ts` 的“并发演示”通过随机选择一个获胜者写入 Mock，并不是真实数据库并发测试。

`src/stores/auth.ts` 使用固定 profiles 和 `switchRole()` 在 user/admin 之间切换，属于纯演示能力；没有登录请求、密码验证、JWT、服务端身份上下文或后端角色判断。

## 5. 当前数据库状态

`sql/schema.sql` 是一个 MySQL 8.x、InnoDB、utf8mb4 的 v1.0 快照，包含用户、分类、会议室、设施、预约、审批记录、操作日志 7 张核心表。当前合理部分包括：

- `reservation` 有 `(room_id, status, start_time)` 查询索引；
- 使用 `DATETIME` 保存完整时间；
- `end_time > start_time` 有 CHECK；
- 外键关系完整；
- 设计文档已经说明应使用 `@Transactional` 和会议室行锁。

仍未满足基线的部分：

- `reservation` 缺少 `request_id` 及唯一约束 `uk_reservation_request_id`；
- 没有 `room_open_rule`，前端只能依赖固定时间段；
- `sys_user.password` 和 `sql/data.sql` 使用明文 `123456`；
- `meeting_room.status` 仍为数字字段，没有在 Java Domain/API DTO 层映射为 `AVAILABLE`、`MAINTENANCE`、`DISABLED`；
- `reservation.status` 的注释和种子数据仍包含 `COMPLETED`；
- `sql/data.sql` 初始化 6 条预约并明确写入 `COMPLETED`，与 v0.2 四状态持久化规则冲突；
- 没有 `sql/migrations/`、版本记录或数据库演进规范；
- 没有开放时间种子数据；
- 没有后端代码验证当前 DDL 可被业务接口使用。

## 6. 当前预约状态模型

前端类型 `src/types/index.ts` 定义为 lowercase：

```text
confirmed | pending | cancelled
```

前端展示层另外推导“待进行 / 已结束 / 已取消”，但没有 `REJECTED`，也没有对四个 API 大写状态建立转换契约。

数据库定义和种子数据使用 uppercase，并且包含：

```text
PENDING / CONFIRMED / REJECTED / CANCELLED / COMPLETED
```

需求规格书不同章节也存在漂移：正文和附录同时出现 `ACTIVE`、`RESERVED`、`IN_USE`、`COMPLETED`、`CANCELLED` 等建议。本次 v0.2 任务明确冻结为唯一持久化集合：

```text
PENDING / CONFIRMED / REJECTED / CANCELLED
```

其中 `PENDING`、`CONFIRMED` 参与冲突；`REJECTED`、`CANCELLED` 不参与冲突。`UPCOMING`、`IN_USE`、`COMPLETED` 只能由当前时间和持久化状态动态推导，不能持续写入数据库。

## 7. 当前会议室状态模型

前端使用 `RoomFlag = 'active' | 'disabled'`，Mock 中返回 `active`；`MeetingRooms.vue` 又将状态用于演示层的 `idle | in-use`。这些其实混合了资源状态和时间占用展示状态。

数据库使用数字：`1` 可用、`0` 维护中、`2` 停用，但没有 API 层的显式枚举映射。

需求规格书要求资源状态为：

```text
AVAILABLE / MAINTENANCE / DISABLED
```

本次应冻结该三值作为 API/Domain 语义；`idle`、`in-use` 只能作为由时间和预约动态推导的展示状态，不能替代资源状态。

## 8. 当前审批语义

当前 SQL 设计已有 `room_category.approval_required`，并在注释中表达普通类别直接确认、受控类别进入待审批。但前端 Pinia 的审批驳回逻辑是 `PENDING -> cancelled`，且页面展示没有统一 `REJECTED` 状态。

本次冻结规则为：

- `approval_required = false`：通过校验后直接 `CONFIRMED`；
- `approval_required = true`：通过校验后 `PENDING`，并立即占用时间资源；
- `APPROVE`：`PENDING -> CONFIRMED`；
- `REJECT`：`PENDING -> REJECTED`；
- 绝不使用 `PENDING -> CANCELLED` 表示审批驳回。

审批属于 administration 的后续能力，但 reservation 生命周期所有权必须由 reservation 域服务承载，不能由其他域直接修改 reservation 表。

## 9. 当前时间冲突算法

`src/utils/conflict.ts` 已实现半开区间重叠判断，核心表达式是：

```text
newStart < oldEnd && newEnd > oldStart
```

这一点与 v0.2 规则一致，并且前端可继续保留为即时 UX 预校验。

但当前实际数据过滤仍只依赖前端 lowercase 状态排除 `cancelled`，没有统一筛选 `PENDING / CONFIRMED`，也没有服务端的 `SELECT ... FOR UPDATE` 临界区。当前算法因此只能证明前端交互提示存在，不能证明最终业务正确性。

## 10. 当前并发方案

当前不存在真实并发控制。`monitor.ts` 的模拟并发：

- 先在同一内存数组中查询是否冲突；
- 随机选择一个“获胜者”；
- 通过 `setTimeout` 写入 Mock；
- 其余请求人为写入 409 监视记录。

这不等价于 100 个 HTTP 请求、MySQL 事务、会议室行锁和最终数据库状态校验。

目标实现必须在 `ReservationService.createReservation()` 的同一事务内先锁定目标 `meeting_room` 行，再查询 `PENDING / CONFIRMED` 冲突，最后写入预约；禁止全局锁、`synchronized` 或先查冲突再插入的非原子方案。

## 11. 当前认证和权限状态

当前没有后端认证。前端通过 `auth.ts` 的固定用户资料和 `switchRole()` 模拟角色，管理员页面主要依赖前端路由和 UI 显隐。

目标基线需要：

- `POST /api/auth/login` 使用 `sys_user` + BCrypt 校验；
- JWT 至少包含 `userId`、`username`、`role`；
- `GET /api/users/me` 从服务端 token 上下文返回当前用户；
- `USER`、`ADMIN` 是唯一冻结角色；
- 真实 API 模式下前端不能通过 `switchRole()` 提升权限；
- 未认证返回 401，越权返回 403，最终授权以服务端为准。

## 12. 当前 API 实现状态

当前源码中未发现后端 HTTP API、Axios/fetch API adapter 或 Vite `/api` 代理配置。页面和组件直接使用 Pinia store，所有业务请求仍为内存操作。

本轮要求的纵向链路均尚未实现：

```text
POST /api/auth/login                  NOT IMPLEMENTED
GET  /api/users/me                     NOT IMPLEMENTED
GET  /api/rooms                        NOT IMPLEMENTED
GET  /api/reservations/calendar       NOT IMPLEMENTED
POST /api/reservations                 NOT IMPLEMENTED
GET  /api/reservations/my              NOT IMPLEMENTED
POST /api/reservations/{id}/cancel     NOT IMPLEMENTED
```

需要统一 `ApiResponse<T>`、错误码、HTTP 状态码和 `GlobalExceptionHandler`，并将真实 API 与 Mock 通过 `VITE_USE_MOCK` 隔离。默认必须是 `false`。

## 13. 当前自动测试状态

仓库当前没有 `backend/`、Maven 测试、JUnit 测试、前端测试、可执行并发脚本或测试报告。前端 package scripts 只有 Vite dev/build/type-check/preview。

现有 `src/utils/conflict.ts` 具备被单元测试的最小条件，但尚未覆盖完全相同、左右重叠、包含、被包含、首尾相接和完全分离 7 类边界。

## 14. 当前 Git 与协作准备度

当前仓库只有根目录前端工程，尚未建立：

- `frontend/`、`backend/` 明确所有权边界；
- 五个业务域的后端 package 边界；
- `sql/migrations/` 独立演进方式；
- API Contract、Domain Boundary、团队开发规范；
- PR 必须包含的测试和构建门槛。

当前分支与远程 main 同步，工作区有两份 Draw.io 未提交修改。仓库不具备五人并行安全拆分条件，原因不是代码量不足，而是数据所有权、API 契约、状态语义和数据库变更流程尚未冻结。

## 15. 最新需求与代码差异总表

| 领域 | 当前代码/SQL | 最新 v0.2 要求 | 最终冻结与修改方向 |
| --- | --- | --- | --- |
| 工程结构 | 根目录 Vue | `frontend/` + `backend/` + `sql/` + `docs/` | 迁移现有 Vue 到 `frontend/`，新增 Spring Boot 模块化单体 |
| 预约状态 | 前端 lowercase 三值；SQL 五值含 `COMPLETED` | 四个大写持久化状态 | 冻结 `PENDING/CONFIRMED/REJECTED/CANCELLED`，展示状态动态推导 |
| 冲突集合 | Mock 排除 lowercase cancelled | `PENDING`、`CONFIRMED` 占用 | DB、Service、API、前端、测试统一 |
| 时间模型 | 前端 date + HH:mm；SQL DATETIME | API 使用完整 datetime | API/DTO 使用 `LocalDateTime`，前端做兼容适配 |
| 会议室状态 | 前端 active/disabled；SQL 数字 | `AVAILABLE/MAINTENANCE/DISABLED` | Java Domain/API 统一枚举，SQL 可保持数字兼容 |
| 审批驳回 | `pending -> cancelled` | `pending -> rejected` | 修正 service、API、前端展示和测试 |
| 幂等 | 无 requestId | requestId + DB unique | v1.1 migration、DTO、duplicate key 处理 |
| 开放时间 | 前端固定时间轴 | `room_open_rule` | 新增表、种子、资源查询模型 |
| 密码 | 明文 `123456` | BCrypt | data.sql 改为 BCrypt 摘要，文档保留测试明文说明 |
| 认证 | `switchRole()` | JWT + BCrypt + 服务端角色 | Mock 兼容保留，真实模式禁止提权 |
| API | 无真实 API | 统一响应、错误、409 | 新增 `common` 契约和前后端 Client |
| 并发 | 随机 Mock 演示 | 事务 + room 行锁 | MyBatis `FOR UPDATE`，无全局锁 |
| 测试 | 无自动化测试 | 算法、Service、集成/并发验证 | JUnit + 可执行并发脚本及说明 |
| 协作 | 无域边界/迁移规范 | 五域、契约、PR 规范 | 新增 development 文档并建立包边界 |

## 16. 计划修改文件与最小破坏策略

预计修改范围如下：

- `frontend/`：迁移现有前端文件，保留页面、组件和主要视觉效果；新增 `shared/api`、模块化 adapter、Mock 开关和 API 类型；
- `backend/`：新增 Java 17 + Spring Boot 3.x + Maven 模块化单体，按五域组织 package；
- `sql/schema.sql`、`sql/data.sql`：维护 v1.1 完整快照和 BCrypt/开放时间种子；
- `sql/migrations/V1_1__team_ready_baseline.sql`：记录增量变化；
- `docs/development/`：新增本报告、核心契约、数据库演进、域边界、API Contract、团队开发规范和最终报告；
- `README.md`：更新为 v0.2 的真实启动方式和当前实现进度；
- 不删除 `src/mock` 的能力，只把它降级为 Mock adapter；
- 不修改或回滚当前两份 Draw.io 未提交工作。

## 17. 五人并行开发判定

当前尚未具备安全的五人并行开发条件，评级为 **NOT READY**。

必须先完成以下基线收敛：

1. 冻结并实现唯一状态、冲突、时间、权限和审批语义；
2. 建立 `frontend/backend` 工程边界和五个后端 Domain package；
3. 建立 migration-only 数据库演进流程；
4. 打通登录、会议室查询、日历、创建预约、我的预约和取消的真实链路；
5. 让创建预约通过 MySQL 行锁、事务、409 冲突和 requestId 幂等验证；
6. 建立 API Contract、Domain Boundary、团队 PR 规范和可重复测试入口；
7. 重新执行前端 type-check/build、后端 `mvn test`，并记录真实结果。

在这些 Gate 全部通过之前，不能评级为 `TEAM READY`。

