# 解析与入库

旧 Operit JSON 入口没有大小阈值，JsonReader 仅隔离不同会话，内部 decodeStreamElement 仍执行 Streams.parse、JsonElement.toString 和 decodeFromString。大对话会同时保留 Gson 树、字符串、ChatMessage 列表，保存时又生成全量消息实体和候选列表。截图中的 StringWriter 扩容与该路径匹配，但截图未提供完整调用尾部和实际导入文件，不能将独立复现等同于对原文件的设备复现。

新 OperitChatStreamReader 按 token 将当前会话的 messages 数组暂存至应用私有 cache/chat-import。元数据字段取自实际归档和旧 ChatHistory 序列化描述符，messages 用空数组占位后独立解码；未知字段直接跳过。临时文件使 messages 可以位于 id/title 之前，无需改变已发布格式。

保存时逐条读取消息 JSON，直接生成当前消息的 kotlinx JsonElement，再沿用应用现有序列化器处理 sections、旧版 content 和候选回答，不再转成整会话字符串。每条基础消息及候选实体写入后释放，内存随最大单条消息及其候选、会话/目录元数据变化，不随会话正文总量累积。

每个非空会话在当前聊天互斥锁和同一 Room 事务内更新目录、聊天元数据、消息与候选。解析或校验失败、取消时回滚当前会话。旧 A2UI 文件状态在数据库提交后清理。空会话继续计为跳过，保留旧归档空会话导入目录的语义。已有最近使用时间、用户消息时间、收藏、角色绑定、归档状态和排序字段仍走原有转换及 DAO。

临时文件按会话创建，并在成功、解析失败、保存失败和取消路径删除。空间需求约为当前会话重写后的 JSON 消息数组大小，代价是一次暂存与再次读取；不要求用户改阈值或拆分现有 JSON 备份。CSV、Markdown 和第三方格式仍由各自现有入口处理，本次没有扩展它们的流式粒度。

[DONE] 消息级解析和 JSON 导入事务接入完成。