# TimeSlot Team Development Guide v0.2

## 1. 分支建议

```text
main
develop
feature/identity-*
feature/resource-*
feature/reservation-*
feature/meeting-*
feature/administration-*
```

本基线任务不自动创建或 push 远程分支，不自动 merge main。

## 2. 域边界

每个成员优先在自己 Domain 的代码、测试和文档范围内工作：

- identity：登录、JWT、用户信息；
- resource：会议室、分类、设施、开放规则；
- reservation：日历、创建、本人预约、取消和冲突一致性；
- meeting：未来参会人、签到、通知；
- administration：审批历史、日志、管理员治理、统计读取。

跨域需求先修改 API/Domain Contract，再通过公开 Service 交互，不直接改对方 Mapper 或表。

## 3. 每个 PR 必须包含

1. 自己负责的 Domain 范围；
2. API 路径、请求、响应和错误码变化；
3. 对应 SQL migration（如有数据库变化）；
4. 基本单元/集成测试；
5. `frontend` 的 `npm run type-check`、`npm run build` 或 `backend` 的 `mvn test` 结果；
6. 对核心业务契约、冲突规则、权限和状态的影响说明。

## 4. 数据库协作

- 不直接在共享数据库手工改表后提交代码；
- 新表/字段/索引必须有版本化 migration；
- `schema.sql` 只作为最新完整快照同步维护；
- data seed 的密码必须是 BCrypt；
- 预约状态只能使用四个持久化状态，展示状态不得回写。

## 5. 预约代码规范

- 创建预约必须调用 reservation Domain 的事务 Service；
- 先 `SELECT meeting_room ... FOR UPDATE`，再查冲突；
- 冲突集合固定为 `PENDING/CONFIRMED`；
- 时间区间固定为 `[start,end)`；
- 禁止全局 synchronized、ReentrantLock 或 Controller 直接 Mapper；
- `requestId` 是客户端逻辑提交的幂等身份，数据库唯一约束是最终防线；
- 409 必须返回可识别业务错误码，前端保留表单并刷新局部日历。

## 6. 合并前检查

```powershell
cd frontend; npm run type-check; npm run build
cd ..\backend; mvn test
```

涉及 MySQL 的改动还要记录 schema/migration 执行结果；涉及并发的改动必须运行 `scripts/concurrency-test.ps1` 或说明本地环境限制。

