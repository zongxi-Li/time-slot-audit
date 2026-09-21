<p align="center">
  <img src="frontend/assets/timeSlot.png" alt="TimeSlot Logo" width="150" />
</p>

<h1 align="center">TimeSlot</h1>

<p align="center">
  智会会议室预约与时间冲突检查系统
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Frontend-Vue%203%20%2B%20TypeScript-42b883?style=flat-square&logo=vue.js" alt="Vue 3 + TypeScript" />
  <img src="https://img.shields.io/badge/Backend-Spring%20Boot%203.3.5-6db33f?style=flat-square&logo=spring" alt="Spring Boot 3.3.5" />
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk" alt="Java 21" />
  <img src="https://img.shields.io/badge/Database-MySQL%208-4479A1?style=flat-square&logo=mysql" alt="MySQL 8" />
</p>

---

## 项目简介

TimeSlot 是一个前后端分离的会议室预约系统，围绕“资源可用性、预约冲突、审批、会议执行和运营治理”形成完整业务链路。

项目采用 Vue 3 前端 + Spring Boot 模块化单体后端 + MyBatis + MySQL 的实现方式。预约事实由后端负责裁决，前端通过统一 API adapter 和 Pinia store 展示看板、个人预约、会议执行和管理员工作台。

## 当前能力

| 能力 | 当前实现 |
|:---|:---|
| 身份与权限 | JWT 登录、USER/ADMIN 角色、管理员路由和接口鉴权、部门、信用分与违规记录 |
| 预约核心 | 日历/周视图、创建、修改、取消、预约资格校验、审批状态、版本控制和幂等请求 |
| 冲突保护 | 锁定会议室行后检查 [start, end) 半开区间冲突，避免同一房间并发穿透 |
| 资源治理 | 会议室、分类容量、设施、开放规则、维护计划和报修工单 |
| 会议执行 | 我的会议、参会人、签到、签退、出勤状态和 No-Show 判定 |
| 通知 | 参会人变更、会议提醒、审批和出勤相关通知，支持未读数与已读管理 |
| 管理运营 | 预约审批、强制取消、审计日志、CSV 导出和运营统计 |
| 测试时间 | 全局业务时钟支持 REALTIME/FIXED；管理员可固定或恢复时间，便于测试时间相关规则 |
| 可预约时段 | 全局预约窗口可读，管理员可配置起止分钟 |

当前仓库已经完成主要业务模块的源码和自动化测试建设；正式发布前仍应在目标环境完成数据库、端口、认证和真实浏览器链路的现场验收。

## 架构

~~~mermaid
flowchart LR
    Browser[浏览器] --> Vue[Vue 3 + Pinia + Element Plus]
    Vue --> Adapter[shared/api API adapter]
    Adapter --> Controller[Spring Boot Controller]
    Controller --> Service[Domain Service]
    Service --> Mapper[MyBatis Mapper]
    Mapper --> MySQL[(MySQL 8)]

    Service --> Clock[Business Clock]
    Service --> Notification[Meeting / Notification]
    Controller --> Security[JWT + Spring Security]
~~~

### 后端领域

~~~text
common          统一响应、异常、安全、业务时钟和预约窗口
identity        登录、用户、部门、资格、信用与违规
resource        会议室、分类、设施、开放规则、维护和报修
reservation     预约事实、查询、生命周期和冲突保护
meeting         参会人、签到签退、出勤和通知
administration  审批、强制取消、审计和运营统计
~~~

## 目录

~~~text
TimeSlot/
├─ frontend/       Vue 3 + TypeScript + Vite 前端
├─ backend/        Spring Boot + MyBatis 后端
├─ sql/            schema、种子数据和 V1_1~V1_10 迁移
├─ scripts/        开发启动与并发验证脚本
├─ docs/           开发契约、交付文档、图表和个人文档
├─ start-dev.cmd   Windows 一键启动入口
└─ README.md       项目总览
~~~

## 快速开始

### 环境要求

- Java 21
- Maven 3.9+（或项目环境中的可用 Maven）
- Node.js 20+ 与 npm
- MySQL 8.x
- Windows 开发环境推荐 PowerShell

### 1. 初始化数据库

新建数据库时，schema.sql 和 data.sql 都是从零初始化脚本；data.sql 会清空并重建演示数据，请勿直接用于需要保留数据的数据库。

~~~powershell
mysql --default-character-set=utf8mb4 -u<user> -p < sql/schema.sql
mysql --default-character-set=utf8mb4 -u<user> -p meeting_room < sql/data.sql
~~~

已有数据库只执行迁移，并按文件名顺序执行当前 V1_1 至 V1_10：

~~~powershell
Get-ChildItem sql/migrations/V1_*.sql |
  Sort-Object Name |
  ForEach-Object { Get-Content $_ -Raw | mysql --default-character-set=utf8mb4 -u<user> -p meeting_room }
~~~

演示账号密码均为 123456，仅适用于本地开发：

| 用户名 | 角色 |
|:---|:---|
| admin | ADMIN |
| lzx | ADMIN |
| zhangsan | USER |
| lisi | USER |

### 2. 配置本地环境

在仓库根目录创建 .env.local。该文件已被 Git 忽略，不要提交真实密码或 JWT 密钥。

~~~dotenv
DB_USERNAME=root
DB_PASSWORD=your-local-password
JWT_SECRET=replace-with-a-secret-at-least-32-characters
SERVER_PORT=8081
VITE_API_PROXY_TARGET=http://localhost:8081
VITE_USE_MOCK=false
~~~

SERVER_PORT 和 VITE_API_PROXY_TARGET 必须保持一致。源码默认端口是 8080；如果本机已有服务占用 8080，可像上例一样统一改为 8081。

### 3. 一键启动

~~~powershell
.\start-dev.cmd
~~~

脚本会读取根目录 .env.local，分别打开后端和前端 PowerShell 窗口：

- 前端：<http://localhost:5173>
- 后端：http://localhost:<SERVER_PORT>
- 前端 /api：代理到 VITE_API_PROXY_TARGET

### 4. 手动启动

后端：

~~~powershell
cd backend
mvn spring-boot:run
~~~

前端：

~~~powershell
cd frontend
npm install
npm run dev
~~~

离线演示 Mock 时，将 VITE_USE_MOCK=true 写入 frontend/.env.local；默认真实模式为 false。

## 主要接口

所有真实请求使用 /api 前缀，完整字段和错误码以 [API Contract](docs/开发文档/api-contract-v0.2.md) 为准。

| 领域 | 接口前缀 | 主要能力 |
|:---|:---|:---|
| 身份 | /api/auth、/api/users | 登录、当前用户、资格和违规记录 |
| 资源 | /api/rooms、/api/admin/rooms | 查询、管理、设施、开放规则、维护和报修 |
| 预约 | /api/reservations | 日历、我的预约、创建、修改、详情和取消 |
| 会议 | /api/meetings | 我的会议、参会人、签到、签退和出勤 |
| 通知 | /api/notifications | 列表、未读数、标记已读和全部已读 |
| 管理 | /api/admin/reservations、/api/admin/statistics | 审批、强制取消和统计 |
| 审计 | /api/admin/audit-logs | 查询和 CSV 导出 |
| 测试配置 | /api/system-time、/api/booking-window | 业务时间和全局预约窗口 |

## 关键业务约定

- 持久化预约状态：PENDING、CONFIRMED、REJECTED、CANCELLED。
- 只有 PENDING 和 CONFIRMED 参与时间冲突；展示层的 UPCOMING、IN_USE、COMPLETED 是动态状态。
- 冲突判断使用半开区间：newStart < oldEnd && newEnd > oldStart。
- 同一会议室的创建流程先取得数据库行锁，再进行冲突查询和写入。
- requestId 用于创建请求幂等；预约修改使用 version 防止覆盖并发更新。
- 业务模块使用统一业务时钟；JWT 过期时间仍使用墙上时钟，避免测试时间影响认证安全。

## 验证

~~~powershell
cd frontend
npm run type-check
npm run build

cd ..\backend
mvn test
~~~

并发验证需要有效 JWT；如果后端运行在 8081，显式传入对应地址：

~~~powershell
python scripts/concurrency-test.py --base-url http://localhost:8081/api --token <JWT> --room-id 1
~~~

脚本默认向同一会议室发送 100 个重叠预约请求，并再次查询日历检查有效预约是否重叠；它不会删除数据库结构。

## 文档导航

- [后端模块说明](backend/README.md)
- [前端模块说明](frontend/README.md)
- [数据库说明](sql/README.md)
- [脚本说明](scripts/README.md)
- [开发文档说明](docs/README.md)
- [核心业务契约](docs/开发文档/core-business-contract.md)
- [领域边界](docs/开发文档/domain-boundaries.md)
- [数据库演进](docs/开发文档/database-evolution.md)
- [接口清单与业务链路](docs/开发文档/会议室预约与时间冲突检查系统_接口清单与业务链路附录.md)

## 开发原则

1. 先确认业务事实的所属领域，再通过公开 Service 或 SPI 跨域调用。
2. 不把数据库 Row/Write、Domain、DTO 和 Projection 混为同一概念。
3. 前端组件通过 store 和 API adapter 访问数据，不在组件内复制后端业务规则。
4. 修改数据库时同步迁移脚本、数据说明和 API 契约。
5. 不提交 .env.local、真实密码、JWT 密钥和本地生成产物。
