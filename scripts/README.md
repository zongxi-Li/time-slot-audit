# 开发与验证脚本

scripts/ 存放不参与应用编译的本地开发和业务验证脚本。

## 启动脚本

- 根目录 start-dev.cmd：Windows 推荐入口。
- start-dev.ps1：读取根目录 .env.local，设置后端/前端环境变量并打开两个 PowerShell 进程。

从仓库根目录运行：

~~~powershell
.\start-dev.cmd
~~~

启动脚本默认使用后端 8080；若配置 .env.local 的 SERVER_PORT=8081，必须同时设置：

~~~dotenv
SERVER_PORT=8081
VITE_API_PROXY_TARGET=http://localhost:8081
~~~

## 并发验证

concurrency-test.py 使用 Python 标准库模拟同一会议室、同一时间段的并发预约，检查返回码和最终日历中的有效区间是否重叠。
Python 脚本和 PowerShell 独立入口默认连接 `http://localhost:8081`；需要使用其他端口时，可通过 `--base-url` 或 `-BaseUrl` 覆盖。
默认会议室为 A302（ID 2），默认时段为明天 10:00–11:00。双击项目根目录的 `run-concurrency-demo.cmd` 即可演示 10 个并发请求；后端未运行时，它会调用 `start-dev.cmd` 启动开发环境并等待接口就绪。若目标时段已有有效预约，会在发出并发请求前退出。演示成功后创建的预约会保留在数据库中。

~~~powershell
python scripts/concurrency-test.py --token <JWT> --count 100
~~~

可通过 --second-room-id 同时验证第二个会议室。脚本不会修改表结构，也不会主动清理预约数据；请在专用测试数据库或可接受数据写入的环境运行。

### 独立演示入口

Windows PowerShell 可以直接运行独立入口。它会自动使用测试账号登录，并把 Token 传给 Python 并发脚本：

~~~powershell
.\scripts\run-concurrency-demo.ps1 -RoomId 2 -Count 10
~~~

如需同时测试两个会议室：

~~~powershell
.\scripts\run-concurrency-demo.ps1 -RoomId 2 -SecondRoomId 1 -Count 10
~~~

也可以指定具体的预约日期、时间段和预约信息：

~~~powershell
.\scripts\run-concurrency-demo.ps1 `
  -BaseUrl http://localhost:8081 `
  -RoomId 2 `
  -Date 2026-09-22 `
  -StartTime 14:00 `
  -EndTime 15:30 `
  -Count 10 `
  -Title "答辩并发演示" `
  -ParticipantCount 5 `
  -Remark "同一会议室同一时间段并发预约"
~~~

直接运行 `concurrency-test.py` 时，也可以使用 `--date`、`--start-time`、`--end-time`、`--title`、`--participant-count` 和 `--remark` 参数覆盖脚本顶部的默认配置。每个会议室预期恰好一个 `201`，否则测试失败；因此请确保目标时间段事先为空。
