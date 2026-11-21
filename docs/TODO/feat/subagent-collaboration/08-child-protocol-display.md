# 子会话协议显示过滤

[返回计划](index.md)

用户对比 Operit-Ry 的独立子会话页面与本方任务窗口，并指出子会话直接显示 meta 提供方协议负载。本次只修正明确的显示缺陷，保留现有窗口交互；独立全屏阅读入口仍是设计建议。

## 原因与修正范围

任务详情将真实消息直接传给通用 MarkdownTextComposable，未接入主聊天的协议显示过滤。Markdown 解析与协议投影承担不同职责，启用 Markdown 不足以保证隐藏提供方负载。

复用 protocolMetaDisplayText，在任务详情与卡片摘要进入渲染前生成显示正文。该过滤器识别 OpenAI Responses reasoning／output item 和 Gemini thought signature，处理未闭合负载，同时保留已闭合代码示例和普通 HTML meta 内容。

报告详情、当前输出和子会话都共用 DetailText，因此统一经过过滤。数据库、运行快照和后续模型请求仍使用原始消息；不能为了界面显示删掉模型继续请求所需的协议记录。独立详情仍清空外层 Markdown 渲染片段。

## 检查与交付

核对过滤调用覆盖详情和卡片两条展示入口，复用现有 ProtocolMetaDisplayFilterTest、ProtocolMetaToolPayloadTest 和 MessageSectionMarkdownTest 的覆盖，不为简单接线新增重复测试。

执行差异空白、UTF-8、文档链接与源码接线检查；完整编译和 JVM 回归由 CI 执行。继续更新 PR #235，显式派发 assembleDebug 和 Android Tests，取得运行链接后不持续轮询。真机上仍需确认旧子会话、活动输出及代码示例的显示。

状态：实现完成，CI 与安装验收待确认。

[DONE]
