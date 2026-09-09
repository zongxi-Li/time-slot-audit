# 智会会议室预约系统（前端 Demo）

课程实训 / 需求展示用原型。Vue 3 + Vite + TypeScript + Pinia + Vue Router + Element Plus。

所有业务数据为前端 Mock（`src/mock/` + Pinia 内存 Store），不接后端，刷新后数据还原。

## 启动

```bash
npm install
npm run dev        # 开发：http://localhost:5173
npm run build      # 类型检查 + 生产构建
npm run type-check # 仅 TypeScript 检查
```

## 功能

- **预约看板**：日视图（会议室 × 时间轴）/ 周视图（周一~周日课程表样式）切换；
  上一周 / 本周 / 下一周导航；会议室筛选；仅显示空闲会议室（日视图）；
  当前时间红线；点击空白格直接带预填信息新建预约。
- **新建预约**：表单校验；提交时进行**时间冲突检测**
  （判定逻辑：`newStart < existingEnd && newEnd > existingStart`，见 `src/utils/conflict.ts`）；
  冲突时展示占用详情，并推荐该时段空闲且容量足够的其他会议室（点击可直接切换）。
- **预约详情**：点击任意预约块打开抽屉，查看完整信息；本人预约可取消（已结束的不可取消）。
- **我的预约**：全部 / 待进行 / 已结束 / 已取消筛选，支持查看与取消。
- **会议室**：容量、设备、当前状态（空闲 / 使用中，按当前时间实时计算）、今日场次与下一场。

### 管理员视角（演示用角色模拟）

页面右上角头像下拉可**切换普通用户（李明）/ 管理员（王建国）视角**；`/admin/*` 路由有守卫，
普通视角访问会被重定向回看板并提示。

- **管理控制台** `/admin/dashboard`：今日/本周预约、待审核、实时并发请求、冲突拦截、
  可用会议室 6 项概览；今日各会议室占用率；待审核队列快捷通过/驳回；最近动态。
- **预约管理** `/admin/bookings`：全量预约表（含已取消），状态/会议室/关键词筛选；
  待审核预约**通过 / 驳回**（驳回需填原因），任意有效预约可**强制取消**（需填原因）。
- **会议室管理** `/admin/rooms`：新增 / 编辑会议室（名称查重、容量、设备）；
  **停用 / 启用**——停用后不可被新建预约选中，已有预约保留展示。
- **系统监控** `/admin/monitor`：并发监视（实时请求、QPS、平均响应、冲突拦截、成功率、冲突率，
  2 秒模拟刷新）+ **「模拟 3 用户并发抢订同一时段」**（真实触发冲突检测：1 个 201 成功并写入看板，
  其余 409 拦截）；请求流水（本会话真实操作实时上报，与模拟心跳混合）；系统指标
  （CPU / 内存 / QPS 迷你柱图、服务状态、运行时长）；审计日志（角色切换、审核、强制取消、
  停用会议室等操作全量留痕）。

## 目录结构

```
src/
├─ components/   # ReservationGrid(日视图) / ReservationWeekGrid(周视图)
│                # ReservationCard / ReservationDialog / ReservationDetail
├─ views/        # ReservationBoard / MyReservations / MeetingRooms
│                # admin/ AdminDashboard / ReservationAdmin / RoomAdmin / SystemMonitor
├─ stores/       # reservation.ts(预约+冲突检测+审核) / meetingRoom.ts
│                # auth.ts(角色模拟) / monitor.ts(监控模拟+审计)
├─ mock/         # reservations.ts / meetingRooms.ts
├─ utils/        # datetime.ts / conflict.ts / grid.ts(时间轴布局)
├─ types/        # index.ts
├─ router/       # index.ts(/admin/* 含 requiresAdmin 守卫)
├─ layouts/      # MainLayout.vue(角色菜单/角色切换)
└─ assets/       # main.css(设计变量)
```

## 接入 Spring Boot + MySQL 时建议替换顺序

1. `src/mock/*` → 后端接口（房间列表、预约列表）；
2. `stores/reservation.ts` 中的 `findConflicts / findAvailableRooms / addReservation / cancelReservation`
   → 改为 API 调用（冲突检测应由后端在事务中完成，前端保留同样的提示交互）；
3. `stores/reservation.ts` 的 `currentUser` → 登录态 / JWT；`stores/auth.ts` 的角色模拟
   → 后端 RBAC（`meta.requiresAdmin` 守卫逻辑可原样保留，仅替换权限来源）；
4. `stores/monitor.ts` 的定时器/模拟流水 → WebSocket 或轮询真实监控接口，审计日志落库；
   审核通过 / 驳回 / 强制取消 / 会议室停用 → 对应管理端接口（建议后端加乐观锁防并发超订）；
5. 组件层无需大改，交互与展示可全部复用。
