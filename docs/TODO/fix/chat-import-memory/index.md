---
fork: https://github.com/3316891527/Operit
branch: fix/chat-import-memory
base: 1e346500fe32e0a81e48aa4412d20d7cfc22c083
---

# 聊天记录导入的消息级内存优化

## 调查

用户截图显示 OutOfMemoryError，扩容请求约 180 MiB，堆栈经过 Gson JsonElementTypeAdapter、JsonWriter、StringWriter。现有 Operit JSON 导入无大小阈值，总会进入 JsonReader，但 decodeStreamElement 会对整个会话执行 Streams.parse → JsonElement.toString → kotlinx.serialization 解码。随后保存阶段同时构建全部消息实体、候选映射和候选实体，单个大对话仍会造成多份全量内容驻留。

400 万字符的阈值只影响文本导出的流式路径与进度，不控制 Operit JSON 导入。其他格式由各自的转换器处理，本次聚焦截图匹配的 Operit JSON 链路。

## 方案与范围

保持新版归档对象和旧版聊天数组的格式与导入入口。按 JSON token 将当前会话的 messages 数组暂存到私有缓存文件，会话元数据单独解析；支持 messages 出现在 id/title 之前。保存时每次只解码一条消息及其候选，沿用现有消息序列化器的 sections/content 迁移，写完即释放，不构造整会话 JSON 树、大字符串或实体列表。

每个会话在同一 Room 事务中更新元数据、替换消息和候选，损坏数据或取消时回滚当前会话，成功后清理旧 A2UI 状态。临时文件在成功、错误和取消路径均清理。不改变整文件跨会话的既有逐会话提交语义，不更改外部接口、数据库结构或添加重试路径。

1. [解析与入库](01-implementation.md)：token 暂存、逐消息解码、事务与取消
2. [验证记录](02-validation.md)：兼容性、失败和受限堆内存回归，PR 与 CI 交付

状态：消息级 JSON 导入及事务接入完成 [DONE]；16 项独立受限堆回归通过，5 项项目模型兼容性测试交由 Android CI，完整集成和设备验收分别记录。