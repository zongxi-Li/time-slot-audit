# TimeSlot Domain Boundaries v0.2

## 1. 总体依赖

```text
identity  ───────┐
                 v
resource ───> reservation ───> meeting
    ^                 ^
    │                 │
    └──── administration (治理/审计，通过公开 Service)
```

更严格的规则是：依赖方向描述服务调用，不代表可以跨域直接写表。每个域只拥有自己的数据和状态变更入口。

## 2. identity

| 项目 | 约定 |
| --- | --- |
| 职责 | 登录、JWT、当前用户、USER/ADMIN 授权 |
| 数据所有权 | `sys_user`、认证配置 |
| 对外 Service | `AuthenticationService`、`CurrentUserProvider` |
| 禁止直接访问 | reservation、meeting_room、approval_record 的 Mapper |
| 上游依赖 | 无业务域依赖 |
| 下游依赖 | 为所有受保护域提供当前用户和角色 |

## 3. resource

| 项目 | 约定 |
| --- | --- |
| 职责 | 会议室、分类、设施、开放时间的读取和资源治理 |
| 数据所有权 | `meeting_room`、`room_category`、`room_facility`、`room_open_rule` |
| 对外 Service | `ResourceQueryService`、后续 `ResourceManagementService` |
| 禁止直接访问 | reservation 状态、approval_record、sys_user 的写 Mapper |
| 上游依赖 | identity（管理员资源治理时） |
| 下游依赖 | reservation 读取资源和规则 |

## 4. reservation

| 项目 | 约定 |
| --- | --- |
| 职责 | 创建、查询、取消、展示状态推导和预约生命周期 |
| 数据所有权 | `reservation` |
| 对外 Service | `ReservationService`、`ReservationLifecycleService` |
| 禁止直接访问 | 其他域的写 Mapper；不可自行修改 room/category/user 表 |
| 上游依赖 | identity 当前用户、resource 资源规则 |
| 下游依赖 | meeting 后续读取预约执行数据；administration 订阅/读取治理结果 |

`ReservationService.createReservation()` 是事务、行锁、冲突和幂等的参考实现。所有入口必须复用它，不得在 Controller 或其他域复制创建逻辑。

## 5. meeting

| 项目 | 约定 |
| --- | --- |
| 职责 | 参会人、签到、通知等预约执行阶段能力（后续） |
| 数据所有权 | 后续新增 meeting 相关表；本轮不创建空表 |
| 对外 Service | 后续 `MeetingExecutionService` |
| 禁止直接访问 | reservation 的状态写 Mapper |
| 上游依赖 | reservation 的公开读取/生命周期 Service |
| 下游依赖 | 无本轮下游 |

## 6. administration

| 项目 | 约定 |
| --- | --- |
| 职责 | 管理员治理、审批行为记录、操作日志、统计读取 |
| 数据所有权 | `approval_record`、`operation_log`；统计优先使用读取模型或公开查询 Service |
| 对外 Service | 后续 `ApprovalService`、`AdministrationQueryService` |
| 禁止直接访问 | 通过 `reservationMapper.updateStatus()` 修改预约；禁止绕过 Reservation Lifecycle Service |
| 上游依赖 | identity 授权、resource 治理、reservation 生命周期 |
| 下游依赖 | 管理端 API/UI |

## 7. 跨域规则

1. Domain 之间通过 Service、DTO、枚举和事件式结果交互，不共享 Entity；
2. Controller 只能调用本域 Service，不能直接调用 Mapper；
3. 跨域读取可以使用对方公开 Query Service，写操作必须调用拥有者的业务 Service；
4. 新增表必须归属一个 Domain，并通过独立 migration；
5. common 只放统一响应、异常、安全上下文、配置、分页和少量无业务含义工具。

