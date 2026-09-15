# meeting

会议执行域（本分支 feature/meeting-execution 已实现）：预约后的参与人、签到/签退、No-Show 判定与个人通知闭环。

## 结构

- `api.ts`：会议执行与通知 API（直接基于 `shared/api/http`，统一 `ApiResponse<T>` 包装）；
- `components/AttendeeManager.vue`：参与人管理 + 出勤总览抽屉（组织者可增删，参与人只读）；
- `components/NotificationBell.vue`：顶栏通知铃铛 + 通知抽屉（60s 轮询未读数）；
- `views/MyMeetings.vue`：我的会议工作台（阶段筛选、签到/签退、参与人入口、出勤历史）。

## 接口（真实后端）

```text
GET    /api/meetings/my                              我的会议（组织者+参与人双视角）
GET    /api/meetings/{reservationId}/attendees       参与人列表
POST   /api/meetings/{reservationId}/attendees       新增参与人 {userId | username}
DELETE /api/meetings/{reservationId}/attendees/{userId} 移除参与人
POST   /api/meetings/{reservationId}/check-in        签到（开始前15分钟开放，结束后关闭）
POST   /api/meetings/{reservationId}/check-out       签退（未签到不可签退，会后60分钟宽限）
GET    /api/meetings/{reservationId}/attendance      出勤总览
GET    /api/notifications?unreadOnly=                通知列表
GET    /api/notifications/unread-count               未读数
POST   /api/notifications/{id}/read                  标记已读
POST   /api/notifications/read-all                   全部已读
```

## 边界

- 预约业务事实通过 reservation 域公开查询服务获取；前端展示状态仅本地推导，不回写 API；
- NO_SHOW 由后端调度判定，前端只展示结果。
