# administration

管理员审批、审批历史、操作审计、运营统计和 CSV 导出的真实 API 前端边界。

- `AdministrationReservations.vue`：待审批列表、详情、通过/驳回和强制取消。
- `AuditLogs.vue`：审计日志筛选与 CSV 导出。
- `OperationsDashboard.vue`：热门会议室、预约高峰和取消率。
- `api.ts`：仅包含 administration 域接口，不向全局 API 文件继续堆叠业务代码。
