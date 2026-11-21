# 验证记录

## 已实际执行

直接编译生产 OperitChatStreamReader.kt 和对应 JUnit 测试，用 64 MiB JVM 堆运行 16 项回归，全部通过。覆盖归档对象、旧聊天数组、messages 在元数据之前、未知字段、Long 最大值、小数精度、Unicode/转义/null、重复 messages 字段、缺失数组、截断数据、回调失败、取消及缓存创建失败。

内存用例通过 Reader 按需生成 25600 条消息，每条正文 4096 字符，总正文 100 MiB，测试输入未事先整份分配。新读取器在 64 MiB 堆下成功完成消息回调并清理缓存。独立 JVM 用同等量输入执行旧 Streams.parse → toString 过程，在相同堆限制下得到 OutOfMemoryError。这验证了整会话驻留问题及读取器优化，不等于完整 Android 导入峰值或原文件真机复现。

独立环境：Kotlin 2.0.21、serialization 1.6.2、coroutines 1.7.3、Gson 2.10、JUnit 4.13.2。Android 项目使用 Kotlin 2.2.21、serialization 1.9.0、coroutines 1.10.2，因此完整集成以项目 CI 为准。

## 项目测试与设备验收

另加入 5 项使用实际应用序列化器的兼容性回归，检查全部会话元数据、旧归档候选及选中索引、结构化片段、旧 ChatHistory 数组和缺失必填字段。此组依赖 Android 项目模型，交给完整 Android JVM CI 执行，本地未将其计入通过数量。

Kotlin 语法、提交规范和本地链接检查结果在 PR 中记录。每个会话的 Room 回滚、实际备份文件导入、导入期间停止操作以及活动聊天的界面行为仍需完整 Android/设备验收。单条超大消息及其候选仍需驻留内存，本次不宣称任意大小单消息均能在有限堆中导入。

构建与测试将在 PR HEAD 发起并核对 SHA，启动后不轮询等待结果；最终状态与真机验收分别记录。

[DONE] 独立受限堆回归完成；完整项目兼容性测试和设备验收未提前标为通过。