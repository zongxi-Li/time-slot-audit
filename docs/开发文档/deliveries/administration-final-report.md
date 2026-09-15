# Administration 开发交付报告

## 交付范围

本分支完成管理域可在当前基线上独立交付的后端、前端、测试、migration 与文档。
严格遵守预约数据所有权：管理域只读取预约数据，不直接调用或修改
`ReservationMapper`，预约状态写入通过 `ReservationLifecyclePort` 编排。

## 后端接口

所有接口均要求 `ADMIN`：

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| GET | `/api/admin/reservations?status=PENDING` | 按状态查询管理员预约列表 |
| GET | `/api/admin/reservations/{id}` | 预约详情及审批历史 |
| POST | `/api/admin/reservations/{id}/approve` | 通过审批 |
| POST | `/api/admin/reservations/{id}/reject` | 驳回审批，原因必填 |
| POST | `/api/admin/reservations/{id}/force-cancel` | 管理员强制取消，原因必填 |
| GET | `/api/admin/audit-logs` | 按操作人、业务类型、时间范围查询审计日志 |
| GET | `/api/admin/audit-logs/export` | 导出 UTF-8 BOM CSV 审计日志 |
| GET | `/api/admin/statistics` | 预约总数、取消率、热门会议室、高峰时段 |

审批通过/驳回成功后分别写入 `approval_record` 和 `operation_log`；强制取消写入
`operation_log`。Service 使用事务保证状态流转失败时不产生虚假审计事实。

## Migration

- 新增：`sql/migrations/V1_6__administration_management.sql`
- 前置版本：包含 `reservation`、`approval_record`、`operation_log` 的 v1.1 基线
- 内容：增加审批时间、审计业务时间、审计操作人时间与预约状态区间查询索引
- 未修改历史 migration 与 `sql/schema.sql`

验证方式：在已执行 v1.1 基线的 MySQL 库运行 migration，使用 `SHOW INDEX` 核对
`idx_approval_created_at`、`idx_operation_business_created`、
`idx_operation_user_created`、`idx_reservation_status_period`。

## 前端页面

新业务代码全部位于 `frontend/src/modules/administration/`：

- 预约审批：待审批列表、详情、通过、驳回、强制取消、审批历史。
- 操作审计：操作人/业务类型/时间筛选与 CSV 导出。
- 运营分析：真实后端聚合的热门会议室使用时长、高峰时段与取消率。
- API 与类型定义收敛在管理域目录，未继续扩大全局 API 文件。

共享路由和导航只做了必要的小范围接线。

## 测试结果

2026-09-15 本地验证：

- `npm ci`：通过，0 个已知漏洞。
- `npm run type-check`：通过。
- `npm run build`：通过；仅有既存的大 chunk 提示。
- `mvn clean test`：通过，共 23 个测试，0 failure、0 error。

后端测试覆盖：

- USER 访问管理接口返回 403，ADMIN 可访问。
- 审批通过与驳回委托预约生命周期端口。
- 驳回原因必填并标准化。
- 重复审批返回 `RESERVATION_INVALID_STATE`。
- 成功审批写入审批记录与操作日志。
- 生命周期依赖缺失时返回 503 且不写虚假审计记录。
- 审计查询条件标准化与数量上限。

测试机仅安装 JDK 17 和 JDK 25；项目要求 Java 21，因此使用 JDK 25 的
`--release 21` 编译，并为旧版 Byte Buddy 测试运行开启实验兼容参数。

## 跨域依赖与限制

当前 `main` 尚无 `ReservationLifecycleService`。按任务书约束，本分支没有复制或
越权实现预约状态更新，而是在
`docs/development/proposals/administration-reservation-lifecycle.md` 记录所需契约。
因此在 Reservation 服务 adapter 合入前：

- 查询、详情、审批历史、审计、统计和 CSV 导出可运行。
- 审批、驳回、强制取消会明确返回 HTTP 503。
- 不会写入不一致的审批/审计记录。

组长集成时必须先合入 Reservation 生命周期公开服务，再增加端口 adapter，并执行
一次 MySQL 事务集成测试，才能宣告审批闭环最终验收通过。

## 未完成 P1

- No-Show 率：meeting 域尚未在 `main` 提供真实出勤读取模型，未伪造数据。
- 公告管理：按优先级未占用审批/审计核心实现时间。
- Excel 导出：已完成真实 CSV 导出，满足至少一种导出格式要求。

## Git 提交

- `c79b435 feat(administration): 实现审批审计与运营统计接口`
- `7e05a80 feat(administration): 接入审批审计与运营分析页面`
- `743a2ce feat(administration): 注册管理域路由与导航`
- `9893515 test(administration): 覆盖审批编排与权限场景`
- 文档提交：本报告与跨域生命周期提案。
