# Meeting 前端模块

会议执行域的前端工作台，处理预约确认后的参会人、签到签退、出勤状态和个人通知，不修改预约本身的生命周期状态。

## 结构

- api.ts：会议执行和通知 API，基于 shared/api/http，统一使用 ApiResponse<T>。
- views/MyMeetings.vue：我的会议、阶段筛选、签到签退、参会人入口和出勤历史。
- components/AttendeeManager.vue：组织者管理参会人，参与人查看出勤总览。
- components/NotificationBell.vue：顶栏通知铃铛、通知抽屉和未读数轮询。

## 接口

~~~text
GET    /api/meetings/my
GET    /api/meetings/{reservationId}/attendees
POST   /api/meetings/{reservationId}/attendees
DELETE /api/meetings/{reservationId}/attendees/{userId}
POST   /api/meetings/{reservationId}/check-in
POST   /api/meetings/{reservationId}/check-out
GET    /api/meetings/{reservationId}/attendance
GET    /api/notifications?unreadOnly=
GET    /api/notifications/unread-count
POST   /api/notifications/{id}/read
POST   /api/notifications/read-all
~~~

## 状态与时间规则

- 会议执行只接受后端确认的 CONFIRMED 预约。
- 签到默认在会议开始前 15 分钟开放，会议结束后关闭。
- 签退要求先签到，并允许会议结束后 60 分钟内补签退。
- 出勤状态包括 EXPECTED、CHECKED_IN、CHECKED_OUT、NO_SHOW。
- No-Show 和提醒由后端根据业务时钟判定，前端只展示服务端事实。
- 管理员固定业务时间或恢复实时后，页面应重新读取相关数据，不能自行伪造预约事实。
