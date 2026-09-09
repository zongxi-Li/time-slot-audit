# TimeSlot · 智会会议室预约与时间冲突检查系统

课程实训项目。目标是做一套**会议室预约 + 时间冲突检测 + 基于分类的差异化审批**的完整系统：
当前已完成 **Vue 3 前端 Demo** 与 **MySQL 数据库设计冻结（v1.0）**，Spring Boot 后端为下一阶段。

> 数据库基线：**Database Schema Freeze: READY**（2026-09-09）——
> 详见 [`docs/database/meeting-room-database-design.md`](docs/database/meeting-room-database-design.md)。

## 项目进度

| 阶段 | 状态 |
| --- | --- |
| 前端原型（Mock 数据，可交互演示全部业务流程） | ✅ 完成 |
| 数据库设计冻结（7 张表 + DDL + 种子数据，本机实测验证） | ✅ 完成 |
| Spring Boot + MyBatis 后端（Controller/Service/Mapper/Entity） | ⬜ 未开始 |
| 登录认证（当前前端为角色模拟，数据库已预留 role 字段） | ⬜ 未开始 |

## 快速开始

### 前端

```bash
npm install
npm run dev        # 开发：http://localhost:5173
npm run build      # 类型检查 + 生产构建
npm run type-check # 仅 TypeScript 检查
```

所有业务数据为前端 Mock（`src/mock/` + Pinia 内存 Store），不接后端，刷新后数据还原。

### 数据库（MySQL 8.x，本地开发）

```bash
mysql -uroot -p < sql/schema.sql   # 自动建库 meeting_room + 7 张表
mysql -uroot -p < sql/data.sql     # 导入联调种子数据
```

- 连接参数：`127.0.0.1:3306`，库名 `meeting_room`；
- 种子数据内置 3 个账号（`admin`/`zhangsan`/`lisi`，演示密码 `123456`）、4 个分类、6 间会议室、覆盖全部 5 种状态的预约；
- 预约时间按导入日相对生成（`CURDATE()` 偏移），任何时候导入都处于未来区间，可直接联调审批/取消流程；
- 建议为项目建独立账号而非直接用 root：

```sql
CREATE USER 'timeslot'@'localhost' IDENTIFIED BY '<你的密码>';
GRANT ALL PRIVILEGES ON meeting_room.* TO 'timeslot'@'localhost';
```

- 可用 Navicat 等工具图形化管理；注意 `schema.sql` 会删表重建、`data.sql` 会清空重灌，两者都是重置基线用。

## 数据库设计要点（已冻结）

- **7 张表**：`sys_user` · `room_category` · `meeting_room` · `room_facility` · `reservation` · `approval_record` · `operation_log`；
- **审批开关在分类上**：`room_category.approval_required` 决定该类会议室"提交即 `CONFIRMED`"还是"提交进 `PENDING` 等管理员审批"，普通会议室不产生审批记录；
- **状态机**：`PENDING / CONFIRMED / REJECTED / CANCELLED / COMPLETED`，`PENDING` 与 `CONFIRMED` 占用时间段；
- **时间冲突规则**（半开区间求交，首尾相接不算冲突）：

```sql
SELECT COUNT(*) FROM reservation
WHERE room_id = ? AND status IN ('PENDING','CONFIRMED')
  AND start_time < ? /*新end*/ AND end_time > ? /*新start*/;
```

- **并发预约**的行锁方案（`@Transactional` + 会议室行 `FOR UPDATE` + 条件 UPDATE）已在设计文档冻结，待后端实现。

完整字段定义、索引分析、ER 图、并发方案见 **[数据库设计冻结文档](docs/database/meeting-room-database-design.md)**。

## 前端功能

- **预约看板**：日视图（会议室 × 时间轴）/ 周视图（课程表样式）切换；会议室筛选；当前时间红线；点击空白格直接新建预约；
- **新建预约**：表单校验；前端同样实现了**时间冲突检测**（`newStart < existingEnd && newEnd > existingStart`，见 `src/utils/conflict.ts`），冲突时展示占用详情并推荐同时段空闲会议室；
- **预约详情 / 我的预约**：查看完整信息，可取消本人预约；
- **管理员视角**（角色模拟，右上角切换）：
  - 管理控制台：预约概览、占用率、待审核快捷处理；
  - 预约管理：全量预约筛选、通过 / 驳回、强制取消；
  - 会议室管理：新增 / 编辑 / 停用启用；
  - 系统监控：并发指标、**模拟 3 用户并发抢订同一时段**（真实触发冲突检测，1 成功其余 409）、审计日志。

## 目录结构

```
├─ sql/            # schema.sql 建表基线 / data.sql 种子数据（数据库唯一出处）
├─ docs/
│  ├─ database/    # 数据库设计冻结文档 v1.0
│  └─ figure/      # 预约流程图等
├─ src/
│  ├─ components/  # 日视图/周视图网格、预约卡片、新建/详情抽屉
│  ├─ views/       # 看板 / 我的预约 / 会议室 + admin/ 四个管理页
│  ├─ stores/      # reservation(冲突检测+审核) / meetingRoom / auth / monitor
│  ├─ mock/        # 前端演示数据
│  ├─ utils/       # datetime / conflict / grid
│  ├─ router/      # /admin/* 含管理员守卫
│  └─ layouts/     # MainLayout（角色菜单/切换）
└─ README.md
```

## 接入 Spring Boot + MySQL 时的建议替换顺序

1. `src/mock/*` → 后端接口（房间列表、预约列表）；
2. `stores/reservation.ts` 的 `findConflicts / findAvailableRooms / addReservation / cancelReservation`
   → 改为 API 调用（冲突检测以**后端事务 + 行锁**为准，前端保留同样的提示交互）；
3. `stores/reservation.ts` 的 `currentUser` → 登录态 / JWT；`stores/auth.ts` 角色模拟
   → 后端权限（数据库 `sys_user.role` 已预留，`meta.requiresAdmin` 守卫逻辑可原样保留）；
4. `stores/monitor.ts` 的模拟监控 → 真实监控接口或 WebSocket；审批 / 强制取消 / 会议室停用
   → 对应管理端接口（并发方案见设计文档第 11 节）；
5. 组件层无需大改，交互与展示可全部复用。

## 文档索引

- [数据库设计冻结文档 v1.0](docs/database/meeting-room-database-design.md) —— 表清单、ER、字段定义、状态机、冲突规则、索引、并发方案、完整 DDL
- [预约流程图](docs/figure/会议室预约流程图.drawio.png)
