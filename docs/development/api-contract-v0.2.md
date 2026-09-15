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
| IMPLEMENTED | GET | `/api/users/me/qualification` | USER/ADMIN | 无 | `QualificationResponse` |
| IMPLEMENTED | GET | `/api/users/me/violations` | USER/ADMIN | 无 | `ViolationResponse[]`（本人记录） |
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

## 5. 身份治理接口（identity 域，v1.3）

管理端统一前缀 `/api/admin`，仅 `hasRole('ADMIN')` 可访问（403 拦截）。

### 5.1 部门

| 状态 | Method | Path | 说明 |
| --- | --- | --- | --- |
| IMPLEMENTED | GET | `/api/admin/departments` | 部门列表 |
| IMPLEMENTED | POST | `/api/admin/departments` | 新增部门 `{deptName,description?}`；重名 400 |
| IMPLEMENTED | PUT | `/api/admin/departments/{id}` | 修改部门 `{deptName,description?}`；被挂靠时不可删除（不提供删除接口） |

### 5.2 用户管理

| 状态 | Method | Path | 说明 |
| --- | --- | --- | --- |
| IMPLEMENTED | GET | `/api/admin/users` | 列表，可选 `keyword`（用户名/姓名模糊）、`status`（0/1） |
| IMPLEMENTED | GET | `/api/admin/users/{id}` | 用户详情，404 校验 |
| IMPLEMENTED | POST | `/api/admin/users` | 新建用户 `{username,password,realName,email?,phone?,role,departmentId?}`；新用户信用分 100，用户名重复 400 |
| IMPLEMENTED | PUT | `/api/admin/users/{id}` | 修改资料 `{realName?,email?,phone?,departmentId?,role?}`；至少一个字段，不能降级自己的角色（403） |
| IMPLEMENTED | PUT | `/api/admin/users/{id}/status` | 启停 `{status:0\|1,reason?}`；不能停用自己（403）；写入 ACCOUNT_DISABLE/ENABLE 违规记录 |
| IMPLEMENTED | PUT | `/api/admin/users/{id}/password` | 重置密码 `{password}`，≥6 位 |
| IMPLEMENTED | GET | `/api/admin/users/{id}/qualification` | 预约资格判定（状态+信用分+限制期） |

### 5.3 信用与违规治理

信用规则（后端 `CreditRules` 常量，身份域唯一权威）：默认 100 分；低于 60 分无预约资格；低于 40 分自动限制 30 天；系统自动限制在信用恢复至 ≥60 后自动解除，人工限制只能人工解除。

| 状态 | Method | Path | 说明 |
| --- | --- | --- | --- |
| IMPLEMENTED | PUT | `/api/admin/users/{id}/credit` | 调整信用分 `{creditChange,reason}`；变化量非 0，原因必填；记录 CREDIT_REWARD/CREDIT_DEDUCT，联动自动黑名单 |
| IMPLEMENTED | PUT | `/api/admin/users/{id}/restriction` | 设置/解除限制 `{reason,restrictedUntil?}`；`restrictedUntil` 为空=解除，非空须为未来时间（400）；不能操作自己（403）；记录 BLACKLIST_SET/RELEASE |
| IMPLEMENTED | GET | `/api/admin/users/{id}/violations` | 违规/信用记录时间倒序，含操作人姓名（系统自动记录 operator 为空） |

`ViolationResponse`：`id,userId,violationType,creditChange,reason,operatorName,createdAt`；`violationType ∈ {CREDIT_DEDUCT,CREDIT_REWARD,BLACKLIST_SET,BLACKLIST_RELEASE,ACCOUNT_DISABLE,ACCOUNT_ENABLE}`。

## 6. 后续接口

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

