# TimeSlot 数据库脚本

sql/ 保存 meeting_room 数据库的 MySQL 8.x 建库脚本和增量迁移。数据库采用 InnoDB、utf8mb4、snake_case、BIGINT 主键和 DATETIME；状态机取值由应用层维护，数据库保存字符串事实。当前结构版本为 **v1.11，共 17 张业务表**。

## 文件职责

| 文件 | 用途 | 注意事项 |
|:---|:---|:---|
| **init.sql** | **一体化建库脚本**：建库 + 17 张表 + 索引/外键/CHECK 约束 + 种子数据 | 推荐入口。会 DROP 并重建全部业务表，不能用于需要保留数据的数据库 |
| schema.sql | 仅表结构（完整快照），供分步执行或结构比对 | 需再执行 data.sql 才有演示数据 |
| data.sql | 仅演示/测试种子数据 | 会清空并重建演示数据，密码为 BCrypt 摘要对应的 123456 |
| migrations/V1_*.sql | 存量数据库增量升级 | 按文件名顺序执行，当前到 V1_11__meeting_execution_record.sql |

init.sql 是 schema.sql 与 data.sql 的合并结果，并补齐了 V1_6 迁移增加、而快照与种子脚本都遗漏的 4 个运营查询索引（`idx_reservation_status_period`、`idx_approval_created_at`、`idx_operation_business_created`、`idx_operation_user_created`），同时修正了 schema.sql 的 DROP 列表漏掉两张单例配置表、导致无法重复执行的问题。

## 表关系

~~~text
department ──< sys_user ──< user_violation
                         └──< reservation ──< approval_record
                                          ├──< meeting_execution        (1:1，主键即 reservation_id)
                                          ├──< reservation_attendee
                                          └──< notification
meeting_room ──< reservation
     ├──< room_facility
     ├──< room_open_rule
     ├──< room_maintenance
     └──< facility_repair_ticket

sys_user ──< operation_log
~~~

实际表结构以 init.sql（或 schema.sql + 对应迁移）为准；执行前请检查当前数据库版本，避免把 reset-style 脚本用于生产或已存在业务数据的数据库。

## 初始化新数据库（推荐：一条命令）

~~~powershell
mysql --default-character-set=utf8mb4 -u<user> -p -e "source sql/init.sql"
~~~

也可在 MySQL 客户端内执行 `source C:/path/to/sql/init.sql`。

> **Windows 下不要用 `Get-Content ... | mysql` 管道执行这些脚本。** PowerShell 会把管道内容按本地代码页重新编码，UTF-8 中文注释会变成乱码并触发 `ERROR 1064` 语法错误。请使用上面的 `source` 方式，或在 cmd 中用 `<` 重定向。

若需要分步执行，也可以：

~~~powershell
mysql --default-character-set=utf8mb4 -u<user> -p -e "source sql/schema.sql"
mysql --default-character-set=utf8mb4 -u<user> -p -e "source sql/data.sql"
~~~

脚本会创建 meeting_room 数据库。若 MySQL 用户没有创建数据库权限，请先由管理员创建数据库并授予权限。

## 升级已有数据库

不要对已有数据库重新执行 init.sql 或 data.sql。按版本顺序执行迁移：

~~~powershell
Get-ChildItem sql/migrations/V1_*.sql |
  Sort-Object Name |
  ForEach-Object { mysql --default-character-set=utf8mb4 -u<user> -p -e "source $($_.FullName)" }
~~~

迁移当前覆盖：团队基线、预约强化、身份治理、资源管理、会议执行、会议实际使用记录、管理运营、状态检查、乐观锁、系统时间和预约窗口。

## 关键数据库约定

- 预约创建由后端锁定 meeting_room 行，再查询冲突并写入。
- 有效冲突状态是 PENDING 和 CONFIRMED。
- 时间区间为半开区间 [start, end)，相邻预约可以首尾相接。
- requestId 和唯一约束支撑创建幂等；version 支撑预约修改的乐观锁。
- reservation.start_time/end_time 是计划时间；meeting_execution 只记录实际起止时间与实际参会人数，一条预约最多一条记录。
- 业务时间配置保存在 system_time_config；JWT 安全时间不由该表控制。
- 数据库账号、密码和 JWT 密钥只放在 .env.local 或环境变量中。

