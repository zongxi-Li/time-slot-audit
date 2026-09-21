# TimeSlot 数据库脚本

sql/ 保存 meeting_room 数据库的 MySQL 8.x 初始化脚本和增量迁移。数据库采用 InnoDB、utf8mb4、snake_case、BIGINT 主键和 DATETIME；状态机取值由应用层维护，数据库保存字符串事实。

## 文件职责

| 文件 | 用途 | 注意事项 |
|:---|:---|:---|
| schema.sql | 从零重建完整表结构 | 会删除并重建业务表，不能用于保留数据的数据库 |
| data.sql | 本地演示/测试种子 | 会清空并重建演示数据，密码为 BCrypt 摘要对应的 123456 |
| migrations/V1_*.sql | 存量数据库增量升级 | 按文件名顺序执行，当前到 V1_10__booking_window_config.sql |

## 表关系

~~~text
department ──< sys_user ──< user_violation
                         └──< reservation ──< approval_record
meeting_room ──< reservation
     ├──< room_facility
     ├──< room_open_rule
     ├──< room_maintenance
     └──< facility_repair_ticket

reservation ──< reservation_attendee ──< notification
reservation ──< operation_log
~~~

实际表结构以 schema.sql 和对应迁移为准；执行前请检查当前数据库版本，避免把 reset-style 脚本用于生产或已存在业务数据的数据库。

## 初始化新数据库

~~~powershell
mysql --default-character-set=utf8mb4 -u<user> -p < sql/schema.sql
mysql --default-character-set=utf8mb4 -u<user> -p meeting_room < sql/data.sql
~~~

schema.sql 会创建 meeting_room 数据库。若 MySQL 用户没有创建数据库权限，请先由管理员创建数据库并授予权限。

## 升级已有数据库

不要对已有数据库重新执行 data.sql。按版本顺序执行迁移：

~~~powershell
Get-ChildItem sql/migrations/V1_*.sql |
  Sort-Object Name |
  ForEach-Object { Get-Content $_ -Raw | mysql --default-character-set=utf8mb4 -u<user> -p meeting_room }
~~~

迁移当前覆盖：团队基线、预约强化、身份治理、资源管理、会议执行、管理运营、状态检查、乐观锁、系统时间和预约窗口。

## 关键数据库约定

- 预约创建由后端锁定 meeting_room 行，再查询冲突并写入。
- 有效冲突状态是 PENDING 和 CONFIRMED。
- 时间区间为半开区间 [start, end)，相邻预约可以首尾相接。
- requestId 和唯一约束支撑创建幂等；version 支撑预约修改的乐观锁。
- 业务时间配置保存在 system_time_config；JWT 安全时间不由该表控制。
- 数据库账号、密码和 JWT 密钥只放在 .env.local 或环境变量中。
