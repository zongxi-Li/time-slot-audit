# 后端领域包说明

`com.timeslot` 下的代码按业务领域组织，而不是按所有 Controller、Service 统一堆放：

- `common`：所有领域共享的安全、异常和响应基础设施。
- `identity`：谁在使用系统，以及用户是否具备预约资格。
- `resource`：可以预约哪些会议室，以及会议室当前是否开放可用。
- `reservation`：预约记录本身和时间冲突规则。
- `meeting`：预约成功后的会议执行、参会和通知。
- `administration`：管理员审批、审计和运营分析。

跨领域调用应优先使用对方公开的 Service 或 SPI，不直接访问其他领域的 Mapper 和数据库表。
