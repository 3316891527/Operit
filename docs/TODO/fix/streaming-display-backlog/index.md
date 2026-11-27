---
repository: https://github.com/3316891527/Operit
branch: fix/streaming-display-backlog
status: implemented-awaiting-ci-and-device-validation
---

# 流式工具内容显示积压

多个工具的参数或结果出现缓慢增长，用户取消后已接收全文立即显示。当前证据指向流式展示落后于共享流缓存，不能据此断言所有工具受影响或归因 QuickJS。

## 范围与实施

1. [增量分发与解析](01-streaming-pipeline.md)：保留文本块、移出主线程解析、增量发布节点，保留协议顺序和回滚
2. [完成与验证](02-completion-and-validation.md)：核对真实完成信号、最后更新和取消，补跨工具与并发回归

本次基于 dev 独立修复，不包含编辑器主题或 QuickJS 未合入补丁。旧字符流入口保持兼容，实际执行和消息保存仍保留完整内容。实现不依赖用户取消，也不以超时或异常切换替代根因修复。

实现已完成：同一 JSON 输入块的连续正文合并，聊天直接使用 String 流，原生分段会话转到后台，周期节点更新只转换脏后缀；共享流修复订阅退出与结束投递竞态。原字符 API、文本与工具顺序、rollback、正常完成和取消行为继续保留。

新增 24 项回归源码：14 项 JVM、10 项 Android。最终静态检查覆盖 15 个变更文件、12 个 Kotlin 文件；源码解析、UTF-8、Markdown 相对链接、冲突标记和差异空白均通过。本地没有执行编译、构建或测试，尚无用例通过结论。

远端发起 assembleDebug 与 Android Tests，后者配置生成仪器测试 APK 对；启动登记后停止进度查询。设备显示延迟是否消失仍待新版验收，不把已发现的性能路径或竞态当成用户现场已经复现的根因。

[DONE]
