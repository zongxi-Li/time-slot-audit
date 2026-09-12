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

## 5. 后续接口

以下接口属于 PLANNED，本轮不伪装为已实现：

```text
GET/POST/PUT /api/rooms/{id}
PATCH /api/rooms/{id}/status
GET /api/rooms/{id}/free-slots
GET /api/rooms/available
PUT /api/reservations/{id}
POST /api/admin/reservations/{id}/approval
GET /api/admin/reservations
```

