# TimeSlot Team-Ready Baseline v0.2

会议室预约与时间冲突检查系统的前后端全栈基线。当前目标是形成可由五名成员并行扩展的模块化单体，而不是一次实现所有管理、通知和统计功能。

## 当前进度

已完成基线工程收敛：

- `frontend/`：Vue 3 + TypeScript + Vite，保留原有日历、周视图、我的预约和管理页面；
- `backend/`：Java 21 + Spring Boot 3.3.x + MyBatis + MySQL + BCrypt + JWT；
- `sql/`：MySQL Schema v1.1 完整快照、BCrypt 种子和独立 migration；
- `docs/development/`：核心业务契约、域边界、API Contract、数据库演进、团队规范和审计报告；
- 真实预约链路包含会议室行锁、半开区间冲突判断、`requestId` 幂等和真实 409 业务错误；
- 前端默认使用真实 API，Mock 仅通过 `VITE_USE_MOCK=true` 开启。

尚未完成或依赖本地环境的部分会在最终报告中明确列出。管理 CRUD、完整审批管理、参会人、通知和统计仍属于后续迭代。

## 目录

```text
time-slot-audit/
├─ frontend/       # Vue 3 前端
├─ backend/        # Spring Boot 模块化单体
├─ sql/            # schema.sql、data.sql、migrations/
├─ scripts/        # 可重复并发验证脚本
├─ docs/           # 需求、设计和开发契约
└─ README.md
```

后端 Domain 包边界：`identity`、`resource`、`reservation`、`meeting`、`administration`。公共层仅放统一响应、异常、安全上下文和配置。

## 1. 初始化 MySQL

需要 MySQL 8.x，并准备一个具备 `meeting_room` 数据库权限的开发账号。不要把密码提交到仓库。

```bash
mysql -u<user> -p < sql/schema.sql
mysql -u<user> -p < sql/data.sql
```

如果已有 v1.0 数据库，按顺序执行：

```bash
mysql -u<user> -p meeting_room < sql/migrations/V1_1__team_ready_baseline.sql
```

种子账号仍使用测试密码 `123456`，数据库中保存的是 BCrypt 摘要：

```text
admin / 123456       ADMIN
zhangsan / 123456    USER
lisi / 123456        USER
```

## 2. 启动后端

项目编译基线为 Java 21。通过环境变量提供数据库配置：

PowerShell：

```powershell
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/meeting_room?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
$env:DB_USERNAME = '<your-db-user>'
$env:DB_PASSWORD = '<your-db-password>'
$env:JWT_SECRET = 'change-this-to-a-long-local-secret'
cd backend
mvn spring-boot:run
```

后端默认监听 `http://localhost:8080`。

## 3. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

Vite 开发服务器默认监听 `http://localhost:5173`，并将 `/api` 代理到 `http://localhost:8080`。首次打开真实模式会进入登录页。

默认模式：

```text
VITE_USE_MOCK=false
```

需要离线演示旧版 Mock 时，在 `frontend/.env.local` 中设置：

```text
VITE_USE_MOCK=true
```

组件不直接感知 Mock/HTTP，数据由 store 和 `frontend/src/shared/api/` adapter 隔离。

## 4. 验证命令

```powershell
cd frontend
npm run type-check
npm run build

cd ..\backend
mvn test
```

已提供 `scripts/concurrency-test.py`，需要后端运行并传入 JWT：

```powershell
python scripts/concurrency-test.py --token <JWT> --room-id 1
```

脚本默认发送 100 个不同 `requestId` 的同房间同时间请求，统计 201/409，并再次查询日历验证有效预约没有重叠。

## 5. 已实现 API

```text
POST /api/auth/login
GET  /api/users/me
GET  /api/rooms
GET  /api/reservations/calendar?start=...&end=...&roomId=...
POST /api/reservations
GET  /api/reservations/my
POST /api/reservations/{id}/cancel
```

预约状态只持久化 `PENDING`、`CONFIRMED`、`REJECTED`、`CANCELLED`。`PENDING` 和 `CONFIRMED` 参与冲突；展示层的 `UPCOMING`、`IN_USE`、`COMPLETED` 由时间动态推导。

## 6. 开发入口

- 先阅读 [Phase 0 审计报告](docs/development/team-ready-baseline-v0.2-audit.md)；
- 业务语义以 [核心业务契约](docs/development/core-business-contract.md) 为准；
- 跨域修改遵循 [Domain Boundaries](docs/development/domain-boundaries.md)；
- API 状态和错误码以 [API Contract](docs/development/api-contract-v0.2.md) 为准；
- 数据库变更遵循 [Database Evolution](docs/development/database-evolution.md)；
- PR 和分支规则见 [Team Development Guide](docs/development/team-development-guide.md)。
