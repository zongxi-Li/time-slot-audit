# TimeSlot API Contract v0.2

统一前缀：`/api`  ；媒体类型：`application/json` ；时间：ISO-8601 `yyyy-MM-dd'T'HH:mm:ss`，服务端按配置时区解释。

## 1. 统一响应

成功：

```json
{"code":"SUCCESS","message":"success","data":{}}
```

失败：

```json
{"code":"RESERVATION_TIME_CONFLICT","message":"该时段已被其他用户占用","data":null}
```

统一错误码：

```text
SUCCESS
VALIDATION_ERROR
UNAUTHORIZED
FORBIDDEN
RESOURCE_NOT_FOUND
ROOM_UNAVAILABLE
ROOM_CAPACITY_EXCEEDED
RESERVATION_TIME_CONFLICT
RESERVATION_INVALID_STATE
DUPLICATE_REQUEST
INTERNAL_ERROR
```

## 2. 本轮接口

| 状态 | Method | Path | 权限 | 请求/查询 | 响应 |
| --- | --- | --- | --- | --- | --- |
| IMPLEMENTED | POST | `/api/auth/login` | 公开 | `{username,password}` | `AuthLoginResponse` |
| IMPLEMENTED | GET | `/api/users/me` | USER/ADMIN | 无 | `UserMeResponse` |
| IMPLEMENTED | GET | `/api/rooms` | USER/ADMIN | 可选 `status` | 会议室列表 |
| IMPLEMENTED | GET | `/api/reservations/calendar` | USER/ADMIN | `start,end,roomId?` | 日历预约列表 |
| IMPLEMENTED | POST | `/api/reservations` | USER/ADMIN | 创建预约请求 | 预约详情，201 |
| IMPLEMENTED | GET | `/api/reservations/my` | USER/ADMIN | 无 | 当前用户预约 |
| IMPLEMENTED | POST | `/api/reservations/{id}/cancel` | 所有人仅本人，ADMIN 可治理 | 可选 `{reason}` | 更新后的预约 |

创建请求：

```json
{
  "requestId":"req-20260911-0001",
  "roomId":1,
  "title":"课程设计讨论",
  "startTime":"2026-09-15T10:00:00",
  "endTime":"2026-09-15T11:00:00",
  "participantCount":6,
  "remark":"需要投影仪"
}
```

预约响应核心字段：`id`、`reservationNo`、`roomId`、`roomName`、`userId`、`userName`、`title`、`startTime`、`endTime`、`participantCount`、`status`、`displayStatus`、`remark`。

## 3. HTTP 状态语义

| HTTP | 使用 |
| --- | --- |
| 200 | 查询、登录、幂等命中既有预约、取消成功 |
| 201 | 首次创建预约 |
| 400 | 参数、时间、开放时间、容量等校验失败 |
| 401 | 缺少或无效 JWT、登录失败 |
| 403 | 角色或资源所有权不足 |
| 404 | 用户、会议室或预约不存在 |
| 409 | 时间冲突、资源状态竞争、非法状态、requestId 语义冲突 |
| 500 | 未预期服务器错误 |

## 4. 前端处理约定

收到预约创建 409 时：

1. 保留 title、participantCount、remark 和当前表单；
2. 显示 `RESERVATION_TIME_CONFLICT` 或资源业务错误消息；
3. 重新请求当前房间和日期的 calendar；
4. 不刷新整个页面；
5. 允许用户重新选择时段或会议室。

## 5. 资源管理接口（feature/resource-management 增量）

会议室、分类、设施、开放规则、维护计划与报修工单由 resource 域提供。管理端接口均要求 ADMIN，业务查询与报修为 USER/ADMIN。

| 状态 | Method | Path | 权限 | 请求/查询 | 响应 |
| --- | --- | --- | --- | --- | --- |
| IMPLEMENTED | GET | `/api/rooms/{roomId}` | USER/ADMIN | 无 | 会议室详情（含设施、开放规则） |
| IMPLEMENTED | POST | `/api/rooms/{roomId}/repair-tickets` | USER/ADMIN | `{facilityId?,facilityName?,issue}` | 报修工单 |
| IMPLEMENTED | POST | `/api/admin/rooms` | ADMIN | `SaveRoomRequest` | 会议室 |
| IMPLEMENTED | PUT | `/api/admin/rooms/{roomId}` | ADMIN | `SaveRoomRequest` | 更新后的会议室 |
| IMPLEMENTED | POST | `/api/admin/rooms/{roomId}/status` | ADMIN | `{status}` | 更新后的会议室 |
| IMPLEMENTED | PUT | `/api/admin/rooms/{roomId}/facilities` | ADMIN | 设施全量列表（整体替换） | 替换后的设施列表 |
| IMPLEMENTED | PUT | `/api/admin/rooms/{roomId}/open-rules` | ADMIN | 开放规则全量列表（整体替换） | 替换后的开放规则列表 |
| IMPLEMENTED | POST | `/api/admin/rooms/{roomId}/maintenance` | ADMIN | `{reason,startTime,endTime}` | 维护计划 |
| IMPLEMENTED | GET | `/api/admin/rooms/{roomId}/maintenance` | ADMIN | 无 | 维护计划列表（倒序） |
| IMPLEMENTED | POST | `/api/admin/rooms/{roomId}/maintenance/{planId}/finish` | ADMIN | 无 | 完成登记后的计划 |
| IMPLEMENTED | GET | `/api/admin/room-categories` | ADMIN | 无 | 分类列表 |
| IMPLEMENTED | POST | `/api/admin/room-categories` | ADMIN | `SaveCategoryRequest` | 分类 |
| IMPLEMENTED | PUT | `/api/admin/room-categories/{categoryId}` | ADMIN | `SaveCategoryRequest` | 更新后的分类 |
| IMPLEMENTED | GET | `/api/admin/repair-tickets` | ADMIN | 可选 `roomId` | 工单列表（OPEN 优先） |
| IMPLEMENTED | POST | `/api/admin/repair-tickets/{ticketId}/resolve` | ADMIN | `{remark}` | 解决后的工单 |

资源域不新增错误码：设施/工单/会议室不存在复用 `RESOURCE_NOT_FOUND`，参数与状态类校验（设施不属于该会议室、工单已处理、维护计划已结束等）复用 `VALIDATION_ERROR`。

工单状态机：`OPEN → RESOLVED`（条件更新兜底并发重复解决，返回 400「该报修工单已处理」）；维护计划状态机：`PLANNED → FINISHED`。设施与开放规则采用「全量替换」语义，避免增量 diff 契约。

## 6. 后续接口

以下接口属于 PLANNED，本轮不伪装为已实现：

```text
GET /api/rooms/{id}/free-slots
GET /api/rooms/available
PUT /api/reservations/{id}
POST /api/admin/reservations/{id}/approval
GET /api/admin/reservations
```

