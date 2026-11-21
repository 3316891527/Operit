# 接入和回归

`super_admin:shell` 增加可选参数 `timeoutMs`，为正整数毫秒，默认 30000。内置 `execute_shell` 使用 `timeout_ms`；`Tools.System.shell(command, timeoutMs?)` 负责字段映射。已有只传 command 的调用保持可用，返回字段仍为 command、output、exitCode。

示例：

```json
{"command":"echo OK","timeoutMs":60000}
```

Root 一次性调用的 stdout/stderr 合并返回，最多保留末尾 256 KiB，截断会在 output 中说明。非零退出、超时、启动失败和清理失败均保持失败结果；不自动重放命令。清理在执行期限外进行，超时返回还可能包含最多 2 秒的组清理以及短暂的私有进程释放时间。Shizuku 等其他后端仍使用现有执行器，外层期限不构成对其所有阻塞原生操作的硬时限保证。

TypeScript 源码、编译 JavaScript 和应用打包资产同步更新。JS 桥接回归已加入 PR 检查及 Android Tests 的轻量 Node job；JVM 回归由正常 `:app:testDebugUnitTest` 发现。

已验证：

- 直接编译实际 IsolatedShellCommand 生产源码并运行 18 项 JUnit 回归，通过
- 真进程覆盖 cat 的 stdin EOF、两路大量输出、后台继承管道、正常后台服务存活、慢命令与 echo 并发、超时后继续调用、取消及其他调用不受影响
- 协议回归覆盖取消和超时发生在握手前、超长控制行、启动失败不重放及清理失败不误报成功
- 6 项 Node 桥接回归通过，覆盖默认值、参数传递、失败传播、空命令及打包资产一致性

独立 JVM 环境为 Kotlin 2.0.21、coroutines 1.7.3、JUnit 4.13.2，测试实际生产执行器，不等于完整 Android 编译。项目使用 Kotlin 2.2.21 与 coroutines 1.10.2，完整构建和 JVM 测试交给本提交的 Android CI。

代码检查、CI 链接与最终提交在 PR 中记录。真机仍需验收默认 su、自定义 su、Root 授权延迟、停止对话，以及并发 Shell/流式任务时的一次性调用。现有用户日志没有挂起现场，不能将这次结构修复声称为已复现并定位原事故。

[DONE] 接入与本地回归完成；CI 最终状态和设备验收分别记录，不提前标为通过。
