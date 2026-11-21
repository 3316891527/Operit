---
fork: https://github.com/3316891527/Operit
branch: fix/shell-command-isolation
base: 1e346500fe32e0a81e48aa4412d20d7cfc22c083
reference: OperitRy 5f2b73557fbf787cc3d4f5270b6acd4367adaf66
---

# 超级管理员 Shell 卡住的调查与修复

## 证据与范围

用户提供了应用运行日志和 logcat。应用日志从 2026-11-19 19:05:53 重启后开始，记录中的一次性 Shell 调用均进入 RootShellExecutor 并完成，首个 `echo OK` 用时约 23 ms。日志末尾 ANR 监控计数为 0。日志包含历史模型对后台进程继承管道的解释，但没有此前挂起调用的执行现场或线程栈，因此不能据此确定该次事故根因。

logcat 包含此前日期的系统崩溃以及导出日志时的命令，未找到能够对应本次 Shell 挂起的阻塞栈。原始附件仅供本地调查，不上传用户命令、路径及对话内容。

已核对调用链为 `super_admin:shell` → `Tools.System.shell` → `execute_shell` → `StandardShellToolExecutor` → AndroidShellExecutor → Root 后端。现有 Root 普通与流式调用共用 libsu 主 Shell；Builder 的 10 秒超时仅用于初始化验证，普通命令不受其限制。exec 路径依次读 stdout/stderr，亦有管道背压风险。

## 方案

借鉴 Ry 的独立进程、进程组、执行前握手和令牌校验。仅将一次性工具的 Root 路径改成独立执行，不改终端会话及内部长期流式任务。使用当前 Root 配置启动私有 su，不切换权限后端，不自动重放命令。

stdout 用于有限长的两行控制协议；用户 stdout/stderr 合并读取，保持插件的 command/output/exitCode 返回字段。读取采用可用字节轮询，不等待后台后代关闭管道。无输入接口的命令使用空 stdin。

执行期限由本次工具参数 timeoutMs 决定，默认 30 秒。超时与取消在返回前清理本次进程组；正常结束不杀后台服务，停止读取并关闭本次管道。显式创建其他会话的守护进程不属于本次进程组，需由启动者自行管理。

## 工作单元

1. [独立执行与取消](01-execution.md)：控制协议、输出读取、期限与资源释放
2. [接入和回归](02-integration.md)：工具桥接、插件资产、文档、自动化验证与交付

状态：实现、18 项独立 JVM 回归及 6 项插件桥接回归完成 [DONE]；完整 Android CI 与设备验收结果在 PR 中分别记录。
