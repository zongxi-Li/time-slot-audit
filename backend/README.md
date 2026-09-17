# 后端模块说明

`backend/` 是 TimeSlot 的 Spring Boot 后端，负责认证、会议室资源、预约、会议执行和管理员功能。技术栈为 Java 21、Spring Boot 3.3、Spring Security、JWT、MyBatis 和 MySQL。

## 请求链路

```text
HTTP 请求 → Controller → Service → Mapper → MySQL
                         ↓
                   跨域查询 Service
```

- `common/`：统一响应、异常、JWT 认证和安全配置。
- `identity/`：登录、用户、部门、信用分、违规记录和预约资格。
- `resource/`：会议室、分类、设施、开放时间、维护和报修。
- `reservation/`：创建、查询、修改、取消预约以及时间冲突检查。
- `meeting/`：参会人、签到签退、会议执行状态和通知。
- `administration/`：预约审批、强制取消、审计日志和运营统计。

每个业务域通常包含 `controller/`、`service/`、`domain/`、`dto/` 和 `mapper/`：Controller 暴露接口，Service 编排业务规则，Domain 表示业务对象，DTO 负责接口数据，Mapper 使用 MyBatis 执行 SQL。

## 主要接口

| 领域 | 主要接口前缀 | 作用 |
|---|---|---|
| identity | `/api/auth`、`/api/users`、`/api/admin/users` | 登录、当前用户和用户管理 |
| resource | `/api/rooms`、`/api/admin/rooms` | 查询和管理会议室资源 |
| reservation | `/api/reservations` | 日历、我的预约、创建、修改和取消 |
| meeting | `/api/meetings`、`/api/notifications` | 会议执行、参会人和通知 |
| administration | `/api/admin/reservations`、`/api/admin/audit-logs`、`/api/admin/statistics` | 审批、审计和统计 |

## 阅读入口

预约核心调用链：reservation/controller/ReservationController.java → reservation/service/ReservationService.java → reservation/mapper/ReservationMapper.java。

## 配置与测试

- `src/main/resources/application.yml`：端口、MySQL 和 JWT 配置。
- `src/test/`：单元测试和数据库并发集成测试。
- 运行：`mvn spring-boot:run`。
- 测试：`mvn test`。
