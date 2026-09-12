# TimeSlot Team-Ready Baseline v0.2 冻结审计

日期：2026-09-12  
审计对象：GitHub `main` 当前基线 `df41f97`  
审计目的：确认本地实现已经发布到共享仓库，并冻结为五人并行开发起点。

## 1. 审计结论

本轮审计结论为：**TEAM-READY BASELINE FROZEN**。

代码实现层面和共享仓库层面均已完成基线发布：

- GitHub `main` 已包含前后端分离、Schema v1.1、真实 API、事务/行锁、幂等、测试和文档提交。
- 本地 `main` 与 `origin/main` 指向同一提交 `df41f97`。
- 本轮没有新增业务功能；只修正最终报告中的测试数量和 G5 证据描述。
- `v0.2-team-ready` 标签和 `develop` 分支应从本冻结提交创建并推送。

## 2. 本轮重新执行的验证

| 检查项 | 结果 | 证据 |
|---|---|---|
| `frontend/npm run type-check` | PASS | TypeScript 类型检查退出码 0 |
| `frontend/npm run build` | PASS | Vite 构建成功；仅有大 chunk 警告 |
| `backend/mvn clean test` | PASS | 15 tests，0 failures，0 errors |
| GitHub 远程地址 | PASS | `https://github.com/zongxi-Li/time-slot-audit.git` |
| GitHub `main` 同步 | PASS | 远程与本地均为 `df41f97` |
| 密码/临时产物检查 | PASS | 未发现 SQL 密码、`target/`、`node_modules/`、日志或 Playwright 产物进入 Git |

## 3. 已继承的真实运行证据

以下证据来自上一轮基线实机验收，本轮未改变相关代码，因此作为冻结审计的有效输入：

- MySQL 8.0.43 初始化成功，种子数据为 3 个用户、6 个会议室和 42 条开放时间规则。
- Spring Boot 使用 Java 21 连接真实 MySQL 成功。
- 登录、`/api/users/me`、`/api/rooms`、calendar、创建预约和取消预约均已真实请求验证。
- 重复 `requestId` 返回已有预约，不产生重复记录。
- 冲突预约返回 HTTP 409 与 `RESERVATION_TIME_CONFLICT`。
- 同一会议室 100 请求最终为 `201: 1`、`409: 99`；两个不同会议室并行竞争互不使用全局锁。
- Playwright 已验证真实 API 登录后加载 6 个 MySQL 会议室和日历数据。

## 4. 冻结后的业务边界

本版本只冻结以下参考实现：

- `identity`：登录、JWT、当前用户。
- `resource`：会议室真实读取、类别、设施和开放规则读取。
- `reservation`：预约创建、查询、取消、冲突、行锁和幂等参考实现。
- `meeting`：保留域边界，暂不实现参会人、签到和通知。
- `administration`：保留审批、日志和统计边界，暂不实现完整管理 API。

后续成员不得在本冻结范围内顺手扩展完整会议室 CRUD、用户管理、审批管理、签到、通知或 Dashboard 统计。

## 5. 仍记录为后续基础设施事项

- 当前仍使用受审查的 `sql/migrations/` 文件，没有在 v0.2 冻结中引入 Flyway/Liquibase。
- Flyway 可以作为五人并行开发后的第一个 infrastructure PR，但不阻挡本次 v0.2 发布。
- CI、Testcontainers/MySQL 集成测试和更严格的共享环境校验属于后续增强。

## 6. 冻结 Gate

| Gate | 结果 |
|---|---|
| 核心业务状态、时间区间和冲突集合统一 | PASS |
| Schema v1.1、`request_id`、`room_open_rule` | PASS |
| frontend/backend 分离 | PASS |
| Java 21 + Spring Boot + MySQL 真实运行 | PASS |
| JWT 登录和角色服务端判断 | PASS |
| rooms/calendar/reservation 真实 API | PASS |
| 409 冲突和数据库行锁 | PASS |
| requestId 真实 MySQL 幂等 | PASS |
| 前端 type-check/build | PASS |
| 后端 clean test：15 tests | PASS |
| 五个 Domain 和协作文档 | PASS |
| 共享 GitHub `main` 已发布 | PASS |

## 7. 发布动作

冻结提交：`df41f97`  
发布标签：`v0.2-team-ready`  
并行开发集成分支：`develop`  

后续业务分支统一从 `develop` 创建：

```text
feature/reservation-*
feature/resource-*
feature/identity-*
feature/meeting-*
feature/administration-*
```

冻结后只接受修复性变更和明确经过评审的基础设施变更；新增业务功能必须由对应 Domain 负责人通过独立 PR 开发。
