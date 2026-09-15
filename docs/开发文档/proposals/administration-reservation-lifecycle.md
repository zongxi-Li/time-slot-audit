# Administration 对 Reservation 生命周期服务的依赖提案

## 背景

管理域拥有 `approval_record`、`operation_log` 与运营统计读取模型，但不拥有
`reservation` 的状态写权限。当前 `main` 未提供可供管理域调用的预约生命周期
Service，因此管理域不能直接调用 `ReservationMapper.updateStatus()` 实现审批。

## 所需公开契约

Reservation 域应提供由 Spring 管理、参与调用方事务的公开服务：

```java
approve(Long reservationId, Long operatorId)
reject(Long reservationId, Long operatorId, String reason)
forceCancel(Long reservationId, Long operatorId, String reason)
```

语义要求：

- `approve` 仅允许 `PENDING -> CONFIRMED`。
- `reject` 仅允许 `PENDING -> REJECTED`，原因必填且不超过 500 字。
- `forceCancel` 允许 `PENDING/CONFIRMED -> CANCELLED`，原因必填。
- 在事务内锁定预约并再次校验当前状态，重复操作返回
  `RESERVATION_INVALID_STATE`。
- 预约不存在返回 `RESOURCE_NOT_FOUND`。

## Administration 侧接入方式

本分支已定义 `ReservationLifecyclePort`。待上述公开 Service 合入 `main` 后，新增
一个很薄的 adapter 委托给 Reservation Service；审批 Service 的现有事务会在状态
流转成功后写入 `approval_record` 和 `operation_log`。若生命周期调用失败，两个审计
事实均回滚。

在 adapter 合入前，查询、审批历史、审计、统计和导出可独立运行；审批、驳回和
强制取消接口会返回 HTTP 503，并明确提示生命周期服务尚未接入，不会越权写表或
制造虚假的审批记录。

## 集成验收

1. 合入 Reservation 生命周期服务与 adapter。
2. 执行 `V1_6__administration_management.sql`。
3. 用特殊会议室创建 `PENDING` 预约。
4. 分别验证通过、驳回、重复审批和强制取消。
5. 核对预约状态、`approval_record`、`operation_log` 在同一事务结果中保持一致。
