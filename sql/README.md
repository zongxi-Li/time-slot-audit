# 数据库模块说明

`sql/` 保存会议室预约系统的 MySQL 数据库脚本，数据库名称为 `meeting_room`。

- `schema.sql`：完整数据库结构，包含用户、部门、会议室、设施、开放规则、预约、审批、日志、维护和报修表。
- `data.sql`：本地演示和测试所需的初始数据。
- `migrations/`：已有数据库的增量升级脚本，按 `V1_1` 到 `V1_8` 顺序执行。

## 关键关系

```text
sys_user → reservation ← meeting_room ← room_category
    ↓                         ↓
user_violation          approval_record / operation_log
                              ↓
                 meeting execution / notification
```

预约创建由后端锁定 `meeting_room` 行后检查时间冲突。冲突条件是半开区间 `[start, end)` 重叠，即新预约开始时间早于旧预约结束时间，且新预约结束时间晚于旧预约开始时间。

## 使用方式

首次初始化执行 `schema.sql` 和 `data.sql`；已有旧库时按版本顺序执行 `migrations/`。数据库账号和密码只放在项目根目录的 `.env.local` 或环境变量中，不要提交到 Git。
