# meeting 域交付报告：会议执行与个人工作台

分支：`feature/meeting-execution`（基于 main @ dc2e296）
负责人：闫佳馀（2413042121）
日期：2026-09-15
状态：待集成评审（未自行合并 PR）

## 1. 交付范围

预约后的用户侧执行闭环，全部为真实后端实现，无 Mock、无空表：

| P0/P1 | 能力 | 状态 |
| --- | --- | --- |
| P0 | 参与人管理（增/删/查，权限与人数约束） | 已完成 |
| P0 | 签到/签退（窗口、幂等、服务器时间） | 已完成 |
| P0 | No-Show 判定（结束后调度、幂等、可重复执行） | 已完成 |
| P0 | 用户前端闭环（我的会议工作台、参与人、签到签退、出勤历史、通知入口） | 已完成 |
| P1 | 业务内通知（被加入/移出、开始提醒、No-Show 结果，列表与已读） | 已完成 |

## 2. 数据模型（migration）

新增 `sql/migrations/V1_5__meeting_execution.sql`（owner：meeting）：

- `reservation_attendee`：执行事实表。`attendee_role(ORGANIZER/ATTENDEE)`、
  `attendance_status(EXPECTED/CHECKED_IN/CHECKED_OUT/NO_SHOW)`、`check_in_at/check_out_at`；
  唯一键 `(reservation_id, user_id)` 防重；状态机由应用层维护，SQL 层 WHERE 条件兜底。
- `notification`：个人通知。`uk_notification_dedup(user_id, dedup_key)` + `INSERT IGNORE`
  保证调度类通知幂等；预留审批/取消类 type 取值。
- 未修改任何既有表，未改 `sql/schema.sql`（按协作约束，快照由组长集成时统一同步）。

**升级前置版本**：v1.1（`V1_1__team_ready_baseline.sql`）。
**验证方式**：`mysql -uroot meeting_room < sql/migrations/V1_5__meeting_execution.sql`；
本环境 MySQL80 服务停止且无管理员权限启动，未能在本机执行，需评审时在共享库复跑。
**回滚**：`DROP TABLE notification; DROP TABLE reservation_attendee;`（均为本域事实数据，可安全回滚）。

## 3. API 变更

统一 `ApiResponse<T>` 包装；错误码复用现有全局约定，未新增错误码。

| Method | Path | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/meetings/my` | 登录用户 | 我参与/组织的会议执行视图 |
| GET | `/api/meetings/{reservationId}/attendees` | 登录用户 | 参与人列表（自动幂等补齐组织者行） |
| POST | `/api/meetings/{reservationId}/attendees` | 创建人或 ADMIN | 新增参与人，body `{userId}` 或 `{username}`，201 |
| DELETE | `/api/meetings/{reservationId}/attendees/{userId}` | 创建人或 ADMIN | 移除参与人 |
| POST | `/api/meetings/{reservationId}/check-in` | 参与人本人 | 签到 |
| POST | `/api/meetings/{reservationId}/check-out` | 参与人本人 | 签退 |
| GET | `/api/meetings/{reservationId}/attendance` | 创建人/ADMIN/参与人 | 出勤总览 + 当前用户出勤 |
| GET | `/api/notifications?unreadOnly=` | 登录用户 | 通知列表（上限 100 条） |
| GET | `/api/notifications/unread-count` | 登录用户 | 未读数 |
| POST | `/api/notifications/{id}/read` | 本人 | 标记已读 |
| POST | `/api/notifications/read-all` | 本人 | 全部已读 |

错误码映射：越权 `FORBIDDEN`；状态/窗口类违规 `RESERVATION_INVALID_STATE`；
参数与人数上限 `VALIDATION_ERROR`；重复参与人 `DUPLICATE_REQUEST`；
预约/用户/通知不存在 `RESOURCE_NOT_FOUND`。

## 4. 业务规则（meeting 域补充语义，不触碰冻结契约）

1. **执行前置条件**：签到/签退仅限 `CONFIRMED` 预约；`PENDING` 未确认不可签到；
   `CANCELLED/REJECTED` 禁止一切执行与参与人结构变更（只读保留历史）。
2. **组织者行**：预约创建人以 `ORGANIZER` 行存在，首次触达执行流程时 `INSERT IGNORE`
   幂等补齐；组织者可签到、可被 No-Show，不可被移出。
3. **签到窗口**：`[start-15min, end)`，服务器时间（`Clock` Bean）记录；
   窗口外返回明确业务错误。
4. **签退窗口**：签到后至 `end+60min` 宽限；未签到不能签退。
5. **幂等**：重复签到/签退直接返回当前出勤事实（无副作用）；SQL 仅允许
   `EXPECTED->CHECKED_IN`、`CHECKED_IN->CHECKED_OUT`、`EXPECTED->NO_SHOW` 转换。
6. **参与人上限**：活跃参与人行数 ≤ 预约申报 `participantCount`（该值创建时已对
   会议室容量校验），上限数值通过 reservation 公开查询服务获取。
7. **移除限制**：仅 `EXPECTED` 且非组织者可移除；已有出勤事实不可移除。
8. **No-Show 判定**：调度每分钟扫描 `CONFIRMED && end_time <= now`（回扫 7 天），
   `EXPECTED -> NO_SHOW`；SQL 仅命中 EXPECTED 行，重复执行影响行数为 0，无重复副作用。
   未使用常驻循环线程。
9. **时间语义**：继续遵守 `[start, end)` 半开区间；展示状态（待开始/进行中/已结束）
   前后端均本地推导，绝不回写 `reservation.status`。

## 5. 跨域边界与集成点

- **不直接修改其他域 Mapper/表**。写操作仅限 `reservation_attendee`、`notification`。
- **新增 `reservation/service/ReservationQueryService.java`（只读，纯新增文件）**：
  为 meeting 提供预约业务事实（状态/时间/归属/申报人数），供组长评审后由 reservation
  域认领维护。内部复用既有 `findById` / `findCalendar`，未新增 SQL 语义。
- meeting Mapper 对 `sys_user/reservation/meeting_room` 仅做**只读 JOIN**（身份解析、
  我的会议展示），与基线 `ReservationMapper` JOIN `sys_user` 的既有先例一致。
- **identity 信用影响**：接口未就绪，本版本仅记录集成点——NO_SHOW 判定后应调用
  identity 对外 Service 扣减信用；当前不写 identity 任何表。
- **预约取消/审批结果通知**：依赖 reservation/administration 域回调，域服务尚未提供
  挂钩点，通知 type 已预留，未擅自改动对方代码。

## 6. 前端

新代码全部位于 `frontend/src/modules/meeting/`：

- `api.ts` + `index.ts`：类型与 API 封装（`shared/api/http` 统一鉴权/包装）；
- `components/AttendeeManager.vue`：参与人管理 + 出勤总览抽屉（管理者可增删）；
- `components/NotificationBell.vue`：顶栏铃铛（60s 轮询未读）+ 通知抽屉（已读/全部已读）；
- `views/MyMeetings.vue`：我的会议工作台（阶段筛选、签到/签退、参与人入口、出勤历史）。

共享文件最小改动（独立 commit，便于冲突处理）：
`router/index.ts` 新增 `/meetings`；`MainLayout.vue` 菜单新增“我的会议”、头部挂载铃铛。

## 7. 测试与验证

`backend/mvn clean test`：**52 个测试全部通过**（本轮新增 37 个）。

- `MeetingExecutionServiceTest`（29）：重复参与人、用户名解析、上限、越权、
  CANCELLED/会后新增、组织者与出勤事实移除保护、签到窗口前后、非参与人签到、
  重复签到幂等、PENDING/CANCELLED 禁止签到、正常签到签退、未签到签退、
  重复签退幂等、宽限期外、No-Show 判定、重复判定无副作用、提醒去重键稳定、
  出勤可见性、预约不存在。
- `ReservationQueryServiceTest`（4）：只读查询过滤语义（仅 CONFIRMED、结束/开始窗口）。
- `NotificationServiceTest`（4）：去重键生成、未拥有/不存在已读报错、视图映射。

`frontend`：`npm run type-check` 通过；`npm run build` 通过（产物含 MyMeetings chunk）。

**真实链路冒烟**：本机 MySQL80 服务处于停止状态且无管理员权限启动，migration 执行与
接口联调需在共享开发库按第 2 节命令复核；种子用户 zhangsan/lisi（密码 123456）可复现
“创建预约 → 添加参与人 → 签到 → 签退 → 结束后 No-Show”全流程。

## 8. 提交清单（Conventional Commits，均已单一职责）

```text
6650210 feat(meeting): 增加参与人与执行阶段数据模型
f790858 feat(reservation): 新增跨域只读预约查询服务
01244be feat(meeting): 增加个人通知与已读能力
3c3997b feat(meeting): 实现参与人管理、签到签退与出勤查询
0f24389 feat(meeting): 实现 No-Show 判定与开始提醒调度
71531f0 test(meeting): 覆盖签到生命周期与异常场景
cbb0693 feat(meeting): 接入个人会议执行工作台
c511ea2 feat(meeting): 添加我的会议路由与顶栏通知入口
```

分支已推送 origin，未创建/合并 PR，由组长集成审查。
