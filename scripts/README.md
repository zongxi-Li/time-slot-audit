# 脚本模块说明

`scripts/` 存放业务并发验证脚本；本地开发启动入口位于仓库根目录。

- `concurrency-test.py`：向预约接口发送同一会议室、重叠时间段的并发请求，用于验证成功数、冲突数和幂等行为。

从仓库根目录运行 `powershell -NoProfile -ExecutionPolicy Bypass -File .\start-dev.ps1` 即可启动开发环境；并发测试需要先准备有效 JWT，并确保后端和数据库已运行。
