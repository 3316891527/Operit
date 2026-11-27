# 增量分发与解析

旧 JSON 转换器把字符串正文拆成单字符 Content 事件，Provider 对每个事件分发并更新 token 估算。现在同一 feed 内的连续正文合并为事件，遇到标签边界立即发布，每个 feed 结束也发布已有正文；不等待完整字符串才显示。转义与跨块 Unicode 语义保留，标签顺序不变。

时间线、cursor 与 bubble 聊天入口直接使用新增 StreamMarkdownChunkRenderer。旧字符入口保持公开兼容，并共用内部渲染实现。恢复前缀继续交给 native block/inline session，保留真实块边界及 continuesPrevious。

原生 session 的创建、初始化、增量 push 与释放使用 Dispatchers.Default。上游收集、累计文本和节点修改仍由调用方上下文持有，避免把界面的非线程安全状态转移到后台。flushMutex 保持会话串行；结束时在不可取消边界先 cancelAndJoin 定时刷新，再释放句柄。跨 dispatcher 创建时先登记句柄，返回途中取消也能清理。

既有 200ms 或换行批处理保留，不能把旧实现描述成逐字符 JNI。每次周期刷新记录最早脏节点，只重建该后缀的不可变快照；已完成历史节点不再重复遍历。结构替换和子节点删除同样标脏，回滚及最终同步可完整核对。这里减少的是历史树遍历、节点及子列表分配，不声称旧代码每帧都会复制所有正文。

[DONE]
