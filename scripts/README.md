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

~~~powershell
python scripts/concurrency-test.py --base-url http://localhost:8081/api --token <JWT> --room-id 1 --count 100
~~~

可通过 --second-room-id 同时验证第二个会议室。脚本不会修改表结构，也不会主动清理预约数据；请在专用测试数据库或可接受数据写入的环境运行。
