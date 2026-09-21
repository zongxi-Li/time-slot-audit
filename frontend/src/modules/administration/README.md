# Administration 前端模块

管理员运营工作台的前端边界，负责预约审批、审计日志和运营统计。真实权限由后端 ADMIN 授权裁决，前端路由守卫只负责改善导航体验。

## 页面

| 文件 | 路由 | 能力 |
|:---|:---|:---|
| OperationsDashboard.vue | /admin/dashboard | 热门会议室、预约高峰、取消率等运营指标 |
| AdministrationReservations.vue | /admin/bookings | 待审批列表、详情、通过、驳回和强制取消 |
| AuditLogs.vue | /admin/monitor | 审计日志筛选和 CSV 导出 |

## API

api.ts 只保留 administration 域的接口适配，不把管理业务继续堆入全局 API 文件：

~~~text
GET  /api/admin/reservations
GET  /api/admin/reservations/{id}
POST /api/admin/reservations/{id}/approve
POST /api/admin/reservations/{id}/reject
POST /api/admin/reservations/{id}/force-cancel
GET  /api/admin/statistics
GET  /api/admin/audit-logs
GET  /api/admin/audit-logs/export
~~~

## 约束

- 管理页面展示的是审批和运营视角，不应把预约生命周期的业务事实复制到前端。
- 审批、驳回、强制取消成功后，以服务端返回重新加载列表和详情。
- CSV 导出由后端生成，前端只负责传递筛选条件和下载响应。
- 任何涉及用户、会议室、通知或会议执行的操作，应调用对应领域的 API，而不是直接复用 administration 内部状态。
