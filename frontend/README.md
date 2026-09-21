<p align="center">
  <img src="assets/timeSlot.png" alt="TimeSlot Logo" width="110" />
</p>

<h1 align="center">TimeSlot Frontend</h1>

基于 Vue 3 的会议室预约工作台，负责登录、预约看板、会议室浏览、我的预约、我的会议和管理员工作台。前端不复制后端裁决逻辑，所有真实数据通过 src/shared/api/ 和 Pinia store 进入页面。

## 技术栈

- Vue 3 + TypeScript
- Vite 6
- Pinia
- Vue Router
- Element Plus

## 页面与路由

| 路由 | 页面 | 权限 |
|:---|:---|:---|
| /login | 登录 | 公开 |
| /board | 预约看板（日历/周视图） | 登录用户 |
| /my | 我的预约 | 登录用户 |
| /meetings | 我的会议、签到签退和出勤 | 登录用户 |
| /rooms | 会议室浏览与详情 | 登录用户 |
| /admin/dashboard | 运营分析 | ADMIN |
| /admin/bookings | 预约审批 | ADMIN |
| /admin/rooms | 会议室、设施、开放规则和维护 | ADMIN |
| /admin/users | 用户、部门、信用和资格 | ADMIN |
| /admin/repairs | 报修工单 | ADMIN |
| /admin/monitor | 操作审计和导出 | ADMIN |

## 前端结构

~~~text
src/
├─ main.ts                 应用入口、Pinia、Router、Element Plus
├─ router/                 路由和登录/管理员守卫
├─ layouts/                主布局、导航、通知和业务时间控件
├─ views/                  登录、预约、会议室和管理员页面
├─ modules/                identity、resource、reservation、meeting、administration
├─ components/             看板、预约详情、弹窗和系统控件
├─ stores/                 auth、reservation、room、meeting、admin、systemTime
├─ shared/api/             HTTP 请求、API adapter 和 TypeScript 类型
├─ utils/                  时间、网格、状态和展示转换
└─ mock/                   仅用于离线演示的 Mock 数据
~~~

页面调用链：

~~~text
router → view/layout → component
                    → Pinia store
                    → shared/api/http.ts
                    → /api → Vite proxy → Spring Boot
~~~

## API 边界

- authApi：登录和当前用户。
- roomsApi / adminRoomsApi：会议室查询与资源管理。
- reservationsApi：日历、创建、修改、取消和我的预约。
- meetingApi：我的会议、参会人、签到签退和出勤。
- notificationsApi：通知列表、未读数和已读操作。
- adminUsersApi / departmentsApi：用户、部门、信用和资格。
- adminCategoriesApi / repairTicketsApi：分类和报修。
- systemTimeApi / bookingWindowApi：业务时钟和预约窗口。

组件只使用 store 或 API adapter，不直接拼接后端业务状态。展示状态可以在前端推导，但预约状态、审批、资格、冲突和权限以服务端响应为准。

## 本地开发

从仓库根目录推荐一键启动：

~~~powershell
.\start-dev.cmd
~~~

手动运行：

~~~powershell
npm install
npm run dev
~~~

默认地址：<http://localhost:5173>。Vite 将 /api 代理到 VITE_API_PROXY_TARGET；该变量应与后端 SERVER_PORT 对应。

真实 API 模式：

~~~dotenv
VITE_USE_MOCK=false
VITE_API_PROXY_TARGET=http://localhost:8081
~~~

离线演示模式，在 frontend/.env.local 中设置：

~~~dotenv
VITE_USE_MOCK=true
~~~

## 验证

~~~powershell
npm run type-check
npm run build
npm run preview
~~~

前端构建成功只证明类型检查和生产打包通过；真实 API、权限和浏览器交互仍需在后端运行后验收。
