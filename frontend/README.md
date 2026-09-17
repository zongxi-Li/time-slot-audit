# 前端模块说明

`frontend/` 是基于 Vue 3、TypeScript、Vite、Pinia、Vue Router 和 Element Plus 的前端应用，负责登录、预约看板、会议室浏览、我的预约和管理员页面。

## 页面链路

```text
main.ts → router → views/layouts → components
                    ↓
                 Pinia stores
                    ↓
              shared/api → /api
```

- `src/main.ts`：创建 Vue 应用并注册 Pinia、Router、Element Plus。
- `src/router/`：页面路由、登录拦截和管理员权限拦截。
- `src/layouts/`：应用整体布局和导航。
- `src/views/`：普通用户和管理员页面。
- `src/components/`：预约卡片、弹窗、日历网格等可复用组件。
- `src/stores/`：登录用户、会议室、预约、用户管理和监控状态。
- `src/shared/api/`：统一 HTTP 请求、接口适配和 TypeScript 接口类型。
- `src/modules/`：按业务域组织的会议、管理、身份、资源和预约入口。
- `src/utils/`：时间、网格、状态和冲突判断工具。
- `src/mock/`：离线演示数据，仅在 `VITE_USE_MOCK=true` 时使用。

## 主要接口

API 统一通过 `src/shared/api/http.ts` 请求 `/api`，开发环境由 Vite 代理到后端：

- `authApi`：`POST /api/auth/login`、`GET /api/users/me`。
- `roomsApi`：`GET /api/rooms`、`GET /api/rooms/{id}`。
- `reservationsApi`：日历、我的预约、创建和取消预约。
- `meetingsApi`、`notificationsApi`：会议、参会人、签到签退和通知。
- 管理 API：用户、部门、会议室、分类、维护、报修、审批、审计和统计。

## 阅读入口

预约看板调用链：src/router/index.ts → views/ReservationBoard.vue → src/stores/reservation.ts → src/shared/api/index.ts。

## 运行命令

```powershell
npm install
npm run dev
npm run type-check
npm run build
```
