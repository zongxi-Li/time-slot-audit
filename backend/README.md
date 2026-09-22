# TimeSlot 后端

backend/ 是 TimeSlot 的 Spring Boot 模块化单体后端，负责认证、预约、资源、会议执行和管理员运营能力。后端不使用 JPA/Hibernate，持久化链路是：

~~~text
HTTP Request
  → Controller
  → Domain Service
  → MyBatis Mapper
  → MySQL
~~~

## 技术栈

- Java 21
- Spring Boot 3.3.5
- Spring Web / Validation / Security
- MyBatis Spring Boot 3.0.3
- MySQL Connector/J
- JWT 0.12.6
- Maven

## 领域模块

| 模块 | 职责 | 代表入口 |
|:---|:---|:---|
| common | 统一响应、异常、安全、业务时钟和预约窗口 | common/api、common/security、common/time |
| identity | 登录、用户、部门、资格、信用和违规 | AuthController、UserController、UserAdminController |
| resource | 会议室、分类、设施、开放规则、维护和报修 | RoomController、RoomAdminController |
| reservation | 预约查询、创建、修改、取消、生命周期和冲突检查 | ReservationController、ReservationService |
| meeting | 参会人、签到签退、出勤、提醒和通知 | MeetingExecutionController、NotificationController |
| administration | 审批、强制取消、审计日志、CSV 和统计 | AdministrationController |

## API 入口

| API 前缀 | 说明 |
|:---|:---|
| /api/auth、/api/users | 登录、当前用户和预约资格 |
| /api/rooms | 用户查询会议室和提交报修 |
| /api/reservations | 日历、我的预约、创建、修改、详情和取消 |
| /api/meetings | 我的会议、参会人、签到、签退和出勤 |
| /api/notifications | 通知列表、未读数和已读操作 |
| /api/admin/users、/api/admin/departments | 用户、部门和信用治理 |
| /api/admin/rooms、/api/admin/room-categories | 会议室、分类、设施、开放规则和维护 |
| /api/admin/repair-tickets | 报修工单处理 |
| /api/admin/reservations | 审批和强制取消 |
| /api/admin/audit-logs、/api/admin/statistics | 审计和运营分析 |
| /api/system-time、/api/booking-window | 业务时钟和预约窗口 |

完整请求字段和错误码见 [接口契约](../docs/开发文档/api-contract-v0.2.md)。

## 关键业务边界

- reservation 是预约事实的拥有者；meeting 通过查询服务读取预约，不直接修改预约状态。
- 创建预约时先锁定对应 meeting_room 行，再执行冲突检查和插入。
- 冲突只考虑 PENDING、CONFIRMED，区间使用 [start, end)。
- 管理员审批通过、驳回和强制取消通过生命周期服务完成，并写入审计记录。
- 通知通过 ReservationNotificationPort 等接口由业务动作触发，通知表使用去重键避免调度重复写入。
- 系统时间用于业务规则和演示测试；JWT 过期仍基于墙上时钟。

## 配置

配置文件：src/main/resources/application.yml。

| 环境变量 | 用途 | 默认/说明 |
|:---|:---|:---|
| DB_URL | MySQL JDBC 地址 | 默认连接 meeting_room |
| DB_USERNAME | 数据库用户 | root |
| DB_PASSWORD | 数据库密码 | 空值，建议通过 .env.local 提供 |
| JWT_SECRET | JWT 签名密钥 | 必须显式提供足够长度的本地密钥 |
| SERVER_PORT | 服务端口 | 8080；端口冲突时与前端代理一起改为 8081 |
| JWT_EXPIRATION_SECONDS | JWT 有效期 | 86400 |

## 运行与验证

从仓库根目录推荐运行：

~~~powershell
.\start-dev.cmd
~~~

手动运行后端：

~~~powershell
cd backend
mvn spring-boot:run
~~~

运行测试：

~~~powershell
mvn test
~~~

## 源码阅读顺序

预约主链路：

~~~text
reservation/controller/ReservationController.java
  → reservation/service/ReservationService.java
  → reservation/service/ReservationLifecycleService.java
  → reservation/mapper/ReservationMapper.java
  → sql/init.sql（一体化建库脚本）
~~~

领域包边界和跨域调用规则见 [com.timeslot 领域边界](src/main/java/com/timeslot/README.md)。
