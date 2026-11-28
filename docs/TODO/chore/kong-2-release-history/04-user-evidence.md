# 用户条目的源码证据

分组报告中的条目存在交叉，数量不能相加作为用户新功能数。所有路径以仓库根目录为起点；只有当前产品头仍有的最终能力进入正式正文。

## 聊天与代理

### 待发送队列

状态：已合入。AI 正在回复或总结时，可把下一条消息和附件放入当前会话的待发送队列。当前会话空闲后可按顺序自动发送，也可管理排队项，把它放回输入框编辑或移除。

入口：聊天输入框的排队发送按钮；输入栏上方的待发送队列

升级边界：每个会话单独排队；编辑须先清空当前草稿、附件与引用；待发条目是本次运行的状态，不作为重启后可恢复的历史。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/viewmodel/PendingMessageQueueStore.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/screens/AIChatScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/modules/input/common/PendingMessageQueuePanel.kt`

提交：`799cf450548ae2fc5ed1cb2555ccbfc84962f632`、`1edc03516a45008984a6eb4e2020f1d3f8878f06`

### 生成时插话

状态：已合入。在待发送队列点「引导当前轮」，可把补充要求交给正在工作的 AI，在它下一次可接收输入时纳入该轮，无需停止整个回答。聊天会按顺序显示补充之前和之后的内容。

入口：待发送队列 → 引导当前轮

升级边界：不是修改已经返回的正文，也不是强制打断提供商正在返回的数据；当前轮不接受时，消息留在队列。

源码：`app/src/main/java/com/ai/assistance/operit/core/chat/TurnInputInbox.kt`、`app/src/main/java/com/ai/assistance/operit/services/core/SteeredTranscript.kt`、`app/src/main/java/com/ai/assistance/operit/services/core/MessageProcessingDelegate.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/screens/AIChatScreen.kt`、`app/src/main/java/com/ai/assistance/operit/api/chat/EnhancedAIService.kt`

提交：`799cf450548ae2fc5ed1cb2555ccbfc84962f632`、`d50766ac642732c1f5eae78084be27723836e144`

### 不发新消息也能继续生成

状态：已合入。已有对话时，可让 AI 根据当前聊天历史继续生成，无需再输入「继续」。不会新增一条空白用户消息，也不会清空输入框草稿。

入口：聊天输入框 → 附件菜单 → 继续生成

升级边界：要有非空用户或 AI 历史；生成、总结及等待自动续写期间不可用。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/modules/attachments/AttachmentSelector.kt`、`app/src/main/java/com/ai/assistance/operit/services/core/MessageCoordinationDelegate.kt`

提交：`ca96a5ee6df4bfddf86ddc473f8e444f15764150`、`a5e525052e2c812cf234a01908405aaf1aa7c59f`、`d90390c67b916c0dbad189a1fa10271b953b83fb`

### 让主代理分派子任务

状态：已合入。为子代理设置独立提示词和可用模型后，主代理可按任务选择帮手、模型与思考选项，委派可独立完成的工作并收集报告。你可以继续在主聊天里交代目标，不用切换主聊天模型。

入口：模型配置 → 子代理配置；功能模型 → 子代理模型池；回到聊天提出协作需求

升级边界：先加入至少一个可用模型并填写实际能力；提示词不绑定固定模型，模型移除后不会自动换成其他模型。协作是否发生取决于主代理判断和工具是否可用。

源码：`app/src/main/java/com/ai/assistance/operit/core/agent/SubagentCatalog.kt`、`app/src/main/java/com/ai/assistance/operit/core/agent/SubagentConfiguration.kt`、`app/src/main/java/com/ai/assistance/operit/core/agent/SubagentCoordinator.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/ModelConfigScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/subagent/SubagentConfigurationContent.kt`

提交：`e81dec5cf20e071091317e4bfbec9d8b00c41a12`、`3a73d3a30eae240775765d1a3868b4ac865eef8e`、`d3c2daee058407afa5f06080e43913e02df7dc20`

### 子代理任务、过程和报告可回看

状态：已合入。从聊天查看这次协作的任务，按状态筛选或搜索，打开任务目标、模型、执行过程、工具卡片、报告和独立子会话。对正在进行的任务补充要求，或只停止一项及其下级，不影响其他分支；也可基于既有子会话继续工作，归档或恢复任务。

入口：聊天输入栏上方 → 子代理协作 → 任务详情

升级边界：补充在下一轮读取；进程结束后活跃执行保留为已中断，不会自动恢复运行。开启自动归档只收起后续成功任务，不清除报告和子会话。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/subagent/SubagentTaskPanel.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/subagent/SubagentConversation.kt`、`app/src/main/java/com/ai/assistance/operit/core/agent/SubagentTaskStore.kt`、`app/src/main/java/com/ai/assistance/operit/core/agent/SubagentCoordinator.kt`

提交：`12872b5d6e653a2d367cd9e005f909f3195ecf83`、`3a73d3a30eae240775765d1a3868b4ac865eef8e`、`30fae4614990d2cf0c66f0a932d8aa2b90008817`、`7263e6d3f4fe898a97507a53c79ed109ab0e0bed`

### 多级子代理协作与运行限制

状态：已合入。子代理可按权限继续分派下级、互相发送协作消息并等待结果；从任务面板查看代理树。派发时可以选择不继承主对话、使用当时的全部可用背景，或继承最近若干用户轮次。可自定义全局及单棵代理树并发、单次时限和深度。

入口：模型配置 → 子代理配置 → 运行限制；聊天中的子代理任务树

升级边界：全局并发 1–64，每棵树 1 至全局上限，时限 1–120 分钟，深度 1–8；排队不占本次执行时限，等待协作让出名额。继承为派发时的快照，不是实时镜像。

源码：`app/src/main/java/com/ai/assistance/operit/core/agent/SubagentCoordinator.kt`、`app/src/main/java/com/ai/assistance/operit/core/agent/SubagentLimits.kt`、`app/src/main/java/com/ai/assistance/operit/core/agent/SubagentContextHistory.kt`、`app/src/main/java/com/ai/assistance/operit/core/agent/SubagentTree.kt`

提交：`d3c2daee058407afa5f06080e43913e02df7dc20`、`30fae4614990d2cf0c66f0a932d8aa2b90008817`、`4486b78d900df9951c58538cc70c19aa1b11e52e`

### 子代理报告补写与产出保留

状态：已合入。子代理有执行记录但未产出最终报告时，可在剩余时间内补写报告；未完成、停止或超时的记录也能保留已捕获的文件产出线索，方便继续工作。

入口：聊天 → 子代理任务 → 概览与结果／执行过程

升级边界：补写报告阶段不调用工具，不保证一定能补写成功；记录到的文件不代表任务完成或验证通过。

源码：`app/src/main/java/com/ai/assistance/operit/core/agent/SubagentCoordinator.kt`、`app/src/main/java/com/ai/assistance/operit/core/agent/SubagentReportRecovery.kt`、`app/src/main/java/com/ai/assistance/operit/core/agent/SubagentReportArtifacts.kt`、`app/src/main/java/com/ai/assistance/operit/core/agent/SubagentReportHistory.kt`

提交：`9d3fddd534da890d9d6934eef19c45d3f91239ec`、`4486b78d900df9951c58538cc70c19aa1b11e52e`

### Waifu 聊天保留连续的思考、工具和回复

状态：已合入。思考与成对的工具调用、结果在 Waifu 聊天里连续展示，不为每个过程另画消息气泡。折叠时显示思考开头，展开可读完整内容；正文仍按节奏分段显示，头像在独立内容后正确出现，已注册自定义表情可用于分段回复。

入口：在聊天中切换到 Waifu 模式后查看正在生成或已保存的回复

升级边界：折叠思考最多预览 8 个 Unicode 码点，超出用省略号；复制原文和展开正文不受预览限制。不要写成移除了思考。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/waifu/WaifuThinkingPreview.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/waifu/WaifuProcessToolCard.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/waifu/WaifuMessageProcessor.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/modules/messages/timeline/ChatTimelineEntries.kt`

提交：`6b5709d226d71bc9fee2d1c6053ffa5f6ef2b1af`、`065e059739128757a488e078d2e9f07c6bf8ec0c`、`2608849c15ec81d27d2f1813be9dc69ae49d2756`、`45d088c93a77d31f356f910988a627f55d77a755`、`230e206a188fd00dc277dfe4474d4ffd7a215ef1`、`eb56a7bfa4e52fb5cd6303f5168b54fbfadf2231`、`f6eca881f35857889b9f80b2f576d75b0a9db20a`、`18b50b82825f61f19ca13abc3a23e292a797bbdd`、`2f0090a4d15ebedfb129b07e6af1ada28d269e36`

### 折叠回复过程与生成详情

状态：已合入。回复完成后，可折叠查看这轮思考及工具过程；展开生成详情查看可用的 Token、缓存和耗时信息。运行中的工具组保持展开，显示剩余调用数，而不是有结果就立刻收起。较长耗时也能以分钟、小时或天显示。

入口：AI 回复下的过程组与生成详情

升级边界：统计仅显示已有数据；不要将未合并的全程实时工具计时视为已交付。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/modules/messages/rendering/ThinkToolsXmlNodeGrouper.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/modules/messages/details/MessageGenerationDetails.kt`

提交：`aa779dac7057f3427d2605047138f939b3d40ed2`、`af2e4deaf9c6117200dbb1fd25ccd690e926bf33`、`4e480e75bb1136f4a20948973c37cddf62b527a3`、`cfdf982b8dfe0875d6d65c77737f9c5aadd43743`

### Markdown 语法与离线 Mermaid 预览

状态：已合入。聊天可显示更多 Markdown 写法，包括高亮、上下标、定义列表和脚注；宽表格可横向滚动。已有的 Mermaid 预览改为原生离线渲染，可预览流程、状态、类、实体关系、思维导图、时序、饼图和甘特图，无需联网。

入口：聊天中的 Markdown 消息；mermaid 代码块的预览按钮

升级边界：旧版依赖在线脚本；新版支持上述图表类型的语法子集，不是完整 Mermaid 实现；配置指令和不支持的或过大的图表会给出错误。

源码：`app/src/main/java/com/ai/assistance/operit/util/markdown/MarkdownProcessorType.kt`、`app/src/main/java/com/ai/assistance/operit/ui/common/markdown/MermaidRenderer.kt`、`app/src/main/java/com/ai/assistance/operit/ui/common/markdown/mermaid/MermaidParser.kt`、`app/src/main/java/com/ai/assistance/operit/ui/common/markdown/StreamMarkdownRenderer.kt`

提交：`67e992a3e51660dfbbeaf31ba0b3e3bdfb070249`、`48831aad311cb72ca5f020fbc27991bddf4de215`、`c993dcdc29165264368b8308f6db2b84291b13c6`、`2b7bf8e3dabbf0cd52faa712edf90b909a402a59`、`08caa5cdb4639525c602380a296e3cfa27963ca9`

### 长回复、代码和工具卡片显示更稳定

状态：已合入。减少长消息加载、分段与流式显示的重复工作；优化长回复末尾的卡片点击和滚动，避免空白气泡与首尾断层。重试回放时区分正文、思考和工具输出，降低旧片段重复显示或内部协议出现在正文的机会。

入口：聊天消息列表；长回复、重试或带工具的请求

升级边界：属于特定显示路径修复；长运行文件读取停滞和显示回退仍未证实完全解决。

源码：`app/src/main/java/com/ai/assistance/operit/util/streamnative/NativeMarkdownStreamOperators.kt`、`app/src/main/java/com/ai/assistance/operit/ui/common/markdown/StreamMarkdownRenderer.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/modules/messages/ChatArea.kt`、`app/src/main/java/com/ai/assistance/operit/api/chat/EnhancedAIService.kt`

提交：`38261aae4270d356369e8813dec4176b374a0758`、`eb90bfdb634b285b25f36e7b91a958d47ebd2c58`、`8d040e60e2e70bf23f7ce5cd451ea0c27e403c30`、`558632b41901d0c04c3e0a048f20dcd753c5b309`、`cbd04afdb66d62e3123230382659c9740ab82110`、`779ad3235811d44b71a56a8a5e1c30d14c76c023`、`8e2379f034cd9624b72de97928fd601548cf31b2`、`fba7b025c53ecb032acce7162de3b038316e0580`、`fe20362349ed2f9673df733541cac3f4fe270817`

### 消息复制与翻译

状态：已合入。复制面板区分可读正文和 Markdown 源文；复制正文时不混入工具协议。消息菜单可直接发起翻译。

入口：长按消息或打开消息操作菜单 → 复制／翻译

升级边界：翻译依赖可用翻译配置；不会替换原聊天内容。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/modules/messages/ChatArea.kt`、`app/src/main/java/com/ai/assistance/operit/core/chat/protocol/MessageSectionCodec.kt`

提交：`ff6d83b4cd0a5054e18e2de40ebedbf3580df933`、`8542070e1878419533dc7eb7c8dbdf079c4229bd`、`5e2a1547d3ed21119438fa4e6a1593ff58720f80`、`c679d67382ec3ec4082174828db2ee6d49169d41`

### 原生交互卡片

状态：已合入。支持模型在聊天中生成可操作的 A2UI 卡片，例如带输入、选择、开关和提交的表单；部分卡片可让当前请求等待，并把提交的结构化数据交还这一轮。

入口：使用支持工具调用的聊天，让 AI 生成交互卡片；在消息里的卡片操作

升级边界：需要对应工具可用；只有支持当前轮等待的卡片才会把提交交回同一轮，历史或已失去上下文的卡片不保证可提交。

源码：`app/src/main/java/com/ai/assistance/operit/ui/common/a2ui/A2uiCard.kt`、`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/a2ui/A2uiTools.kt`、`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/a2ui/A2uiToolRuntime.kt`、`app/src/main/java/com/ai/assistance/operit/services/core/MessageCoordinationDelegate.kt`

提交：`2c9212ae3bfb6122fdaa342375ba214929f5c744`、`b2d7e30741ad40e59c252d8678aa949c929401b9`、`93f36a639ee90920e510e3754ad10ac43261343b`、`ac86467c446da4971f7594fe0b5fb9cec55fb472`、`8d00a696519ba544490a53b7f1d5e7be53bfa555`、`582ea78f58569c8b7fe0d432c0152f0203db23af`、`88cf4f27d5551326c96ac3dd517039aba3316b54`、`26599b5d3a9a69d7ddc1a8706fb4d8c8a090825b`

### 发送前编辑图片

状态：已合入。从聊天附件的图片预览打开编辑，可裁剪、旋转、画笔标注、画矩形、椭圆或箭头，添加马赛克和文字，也可撤销、重做后再发送。

入口：选择聊天图片附件 → 点开预览 → 编辑 → 完成

升级边界：编辑应用于待发附件，不要求覆盖原图；退出未保存时会提示。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/modules/attachments/images/AttachmentImagePreviewDialog.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/modules/attachments/images/editor/ImageEditorDialog.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/modules/attachments/images/editor/ImageEditorControls.kt`

提交：`3420644c8a4974077d8d6570b5aacfc08341e78d`、`4c62b2593df42842633a5d7daecd4b24ab2a3eaa`

### 应用内相册与全屏图片查看

状态：已合入。选择图库附件时可在应用内选图，聊天中的图片可以全屏查看；图片加载失败有更明确的提示，过期图片也会标出，不再只留下不明空白。附加屏幕内容会按图片发送，而不是把它替换成 OCR 文本。

入口：聊天附件菜单／点开消息图片

升级边界：权限、原文件和模型视觉能力仍会影响选图、读取和识别。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/modules/attachments/AttachmentSelector.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/modules/attachments/images/AttachmentImagePreviewDialog.kt`、`app/src/main/java/com/ai/assistance/operit/ui/common/markdown/MarkdownImageRenderer.kt`

提交：`a9a33815e8a77aec80893ae1a08fa53d1c33304d`、`2f0090a4d15ebedfb129b07e6af1ada28d269e36`、`131cc79cc77cf2634a3c5aed3dc077e72575d7aa`、`c848f2c4ffdd4d51bd3e5972fa2cc4490bcd1b99`、`96b44e58e05f7539f253cfa77d16787cb7a31f66`、`ab0efc7b1abac5b7f5acf47346a21fc6419cc696`

### 整理聊天目录与归档

状态：已合入。聊天侧栏整合嵌套目录和角色入口，可批量移动对话。暂时不需要的聊天可归档，在「已归档」中仍可找回并取消归档，不必删除。

入口：聊天侧栏 → 对话目录；聊天菜单 → 归档，切换已归档列表后可恢复

升级边界：归档不是删除或导出。历史操作会区分当前对话、锁定对话和普通对话。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/modules/history/ChatArchiveFilter.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/modules/history/folders/FolderChatHistoryPanel.kt`、`app/src/main/java/com/ai/assistance/operit/data/repository/ChatHistoryManager.kt`

提交：`01bb5b92b999ba7d3e2d606eebd4e603a805f3ca`、`632e16486a3f07b10a84ee79003d92a44c5d085e`、`0caccba48452a1a3690f43c2f4a630f24fa2ec8f`、`722a08c35e03e2ee33538670d1ab80a0e22a5886`

### 聊天备份支持子代理树，大记录分批导入

状态：已合入。继续支持 JSON、Markdown、HTML、TXT 和 CSV 导出。原生 JSON 可保留主对话和子代理任务树；较大的原生 JSON 按消息逐条读取，减少整段历史同时占用内存。保留原生 CSV 的导入和导出，并修正顺序。

入口：设置 → 数据备份 → 聊天记录导入／导出

升级边界：要完整迁移子代理树请用 Operit 原生 JSON；导入时原有活动执行会标为已中断，不会自动运行。1.12.2 已有五种导出，勿表述为 2.0 新增；原生 JSON 由 v2 演进到 v4；旧版未验证能读 v4，不应用新文件覆盖旧备份。

源码：`app/src/main/java/com/ai/assistance/operit/data/repository/ChatHistoryManager.kt`、`app/src/main/java/com/ai/assistance/operit/data/chat/archive/OperitChatArchive.kt`、`app/src/main/java/com/ai/assistance/operit/data/chat/archive/OperitChatStreamReader.kt`、`app/src/main/java/com/ai/assistance/operit/core/agent/SubagentGraphArchive.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/ChatBackupSettingsScreen.kt`

提交：`2411554cda431e6d65e04bc07047058779671edb`、`d8844ce89a482e230f92d3f1593e89592fc40808`、`5ac91cfdf971ec786c7ea9cbcb815a7ba62ec5c5`、`7ea432f305934bf480a1f8eea596602941899654`、`30fae4614990d2cf0c66f0a932d8aa2b90008817`

### 第三方历史导入变更

状态：已合入。内置导入不再单列 ChatGPT、ChatBox 的专用格式；旧导出如需迁移，应先转换成当前支持的格式再导入。

入口：设置 → 数据备份 → 聊天记录导入

升级边界：不能将通用 JSON 当成对旧专用导出的等价支持；外部迁移器不属于这个 HEAD 已内置的功能。

源码：`app/src/main/java/com/ai/assistance/operit/data/converter/ChatFormat.kt`、`app/src/main/java/com/ai/assistance/operit/data/converter/ChatFormatDetector.kt`、`app/src/main/java/com/ai/assistance/operit/data/repository/ChatHistoryManager.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/ChatBackupSettingsScreen.kt`

提交：`2411554cda431e6d65e04bc07047058779671edb`

### 历史消息与回复变体保存更可靠

状态：已合入。聊天历史分别保存正文、思考、工具调用和结果，让旧对话重新打开、回复候选切换、复制与备份恢复后不易混淆这些内容。还修正个别超长历史和包含 NUL 的消息无法正常读取的问题。

入口：打开历史聊天、切换重生成候选或从备份恢复

升级边界：升级时会迁移现有记录；属源码级核对，未实测所有旧库、附件或损坏备份。

源码：`app/src/main/java/com/ai/assistance/operit/data/db/MessageSectionsMigration.kt`、`app/src/main/java/com/ai/assistance/operit/data/db/MessageSectionBackfill.kt`、`app/src/main/java/com/ai/assistance/operit/data/dao/ChatContentDao.kt`、`app/src/main/java/com/ai/assistance/operit/data/model/MessageEntity.kt`、`app/src/main/java/com/ai/assistance/operit/data/model/MessageVariantEntity.kt`、`app/src/main/java/com/ai/assistance/operit/core/chat/protocol/MessageSectionCodec.kt`

提交：`03de658a4ac12dbc80dcca8e5b0be96e6bda2fdb`、`721688b22b0a383a8864fae802d3f0ae1e2df425`、`a4c7cef7e8836f0aa3b5258cf5140fbb40837b90`、`03a5ecc62ece45fa5ee9316140a0b62d8189b616`

### 请求失败、重试与取消提示更清晰

状态：已合入。发送或总结失败时，区分请求、模型响应和本地处理等故障；总结可显示重试进度，相关通知点开可回到来源聊天。修正一次请求结束或取消后未及时收尾，以及连续发送衔接不稳的问题。

入口：聊天状态提示、系统通知及生成中的停止操作

升级边界：重试取决于故障、协议和配置；取消不能撤销已经执行的外部动作。后台通知仍须系统允许。

源码：`app/src/main/java/com/ai/assistance/operit/services/core/MessageProcessingDelegate.kt`、`app/src/main/java/com/ai/assistance/operit/services/core/MessageCoordinationDelegate.kt`、`app/src/main/java/com/ai/assistance/operit/api/chat/EnhancedAIService.kt`、`app/src/main/java/com/ai/assistance/operit/api/chat/AIForegroundService.kt`、`app/src/main/java/com/ai/assistance/operit/ui/main/OperitApp.kt`

提交：`e203baddd62145cbeac5806b5c04fd6127e83fd5`、`fdb9d4334ee61237843fb2975911d5ee5f3eccd1`、`6a4b9bfb399c670321518089592ae3e02e38897e`、`f4b28b216c53553abf92587a3e621189d5733a07`、`8ec87cd22b84699bd6eda09f65489970a8518603`、`86aacf8b61236552b087ef71216f27195ee9fb51`、`5c9ad041ae10c318d5001a84c6b709d5ca33143c`、`5b709b88d979a786cfc425c0507d930783f6538e`、`b7e7922db621152e9a9aebc77c1511f8ef420dba`、`d90390c67b916c0dbad189a1fa10271b953b83fb`、`28af5bb55464667f8bf097ff3ca239025b853e4e`

### 模型响应与工具续轮更稳健

状态：已合入。对仅返回思考却没有可见正文的情况，重试时保留思考历史；若达到输出限制，会提示回复可能未完成。模型截断一批工具调用时，不把不完整批次当成正常工具请求执行。改善 Claude 历史工具结果顺序、Gemini 空内容请求，以及 DeepSeek 思考字段处理。

入口：模型回复、思考、工具调用和错误提示

升级边界：仅涉及相应协议及可识别的截断，不保证提供商都返回完整状态，也不承诺所有长请求都能续完。

源码：`app/src/main/java/com/ai/assistance/operit/api/chat/EnhancedAIService.kt`、`app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/ClaudeProvider.kt`、`app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/GeminiProvider.kt`、`app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/DeepseekProvider.kt`

提交：`51fe8f1f12703f5d648d1469a01e560478a4db76`、`5ed96bffbdf54164e79a58cecca2f26b681f0051`、`4067212991a549a6f265bafc16d2e24e3c081883`、`c0375d6de81c7e3b811effa89fbfcaf5fcb03f7f`、`9f991f480c5227c8224ea6d00ea2ec7cb82c9d5c`、`3f3ec85ccaea5a7281d9de1d31db42732131bb36`

### 上下文占用显示服务端确认值

状态：已合入。可在聊天输入设置查看上下文占用，并在有服务端确认用量时用该输入 Token 数显示，而不是把本地估算误当作确认值。

入口：聊天输入设置 → 上下文

升级边界：确认数值取决于所用服务端返回；不等于应用统计页的累计总账。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/screens/AIChatScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/viewmodel/ChatViewModel.kt`

提交：`72df213a22e419a380d60fbdb1b7176707b0264e`、`d3afdf120bd6bde3c5919024b6ec8882d1d940b9`

### 实时工具耗时与跨分段关联

状态：待合入。工具运行时显示实时耗时，结束后保留对应耗时，并改善普通、Waifu 和子代理聊天中计时与工具卡片的关联。

入口：聊天工具卡片及子代理执行过程

升级边界：待 #283 合入并复核后再加入正式说明；未执行或被截断的调用不应承诺显示运行时长。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/modules/messages/rendering/tooltiming/ToolTimingIndex.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/modules/messages/rendering/tooltiming/ToolTimingTitle.kt`、`app/src/main/java/com/ai/assistance/operit/services/core/SteeredTranscript.kt`

提交：`ee8348e53caa3305e13c06c13b73834bf9e84a28`

## 主题、模型、语音与数据

### 主题与角色外观重整

状态：confirmed。“主题、背景、消息、界面”四页集中编辑，消息背景和输入框支持百分比透明度，各区域可独立覆写玻璃效果。角色编辑分基础、内容、发言、绑定；可确认后将当前视觉主题应用到全部角色，也可在主题页保存全局用户名。

入口：设置 → 主题；角色卡 → 编辑

升级边界：已有主题继续使用原全局玻璃参数；批量操作仅针对视觉主题，不修改角色提示词或绑定。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/theme/ThemeSettingsContentEditor.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/theme/ThemeSettingsTabs.kt`、`app/src/main/java/com/ai/assistance/operit/data/preferences/ActivePromptManager.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/components/CharacterCardDialog.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/components/character/speech/CharacterSpeechSettings.kt`

提交：`083b1d1b`、`075c6435`、`285c628f`、`d96f070a`、`a8040fca`、`01d006e6`

### 原生主题工作室

状态：confirmed。在设计、源码、资源间切换，选择组件、查看原生或全屏预览、撤销重做、维护工程信息，支持批量导入媒体和导入导出 .oktheme 工程，按角色保存工程草稿。

入口：设置 → 主题 → 主题页 → 主题工作室

升级边界：保存工作室草稿不会启用主题；点击“应用到角色”后仍需在主题页保存。工程包不是普通主题配置；只导入信任来源。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/theme/ThemeSettingsBasicTab.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/theme/studio/ThemeStudioScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/theme/studio/ThemeStudioState.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/theme/studio/ThemeStudioSource.kt`、`app/src/main/java/com/ai/assistance/operit/data/resources/theme/ThemeScriptBundleStore.kt`

提交：`8f693b22`、`e9b4a063`、`2531e495`、`a71bf9be`、`2f775380`、`2e9a36b3`

### 页面、文字和按钮独立外观

状态：confirmed。可在工作室调整页面背景与渐变、聊天背景模糊和原生交互效果、弹出菜单形状和位置、系统状态栏、输入框占位文字、各文字目标和描边；动作按钮有独立图标及交互外观。实时页面预览可定位编辑目标。

入口：主题工作室 → 设计 → 选择组件

升级边界：不同组件支持的属性范围不同；未覆写部分沿用原外观。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/theme/studio/ThemeStudioGradients.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/theme/studio/ThemeStudioLivePage.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/theme/studio/ThemeStudioInteraction.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/theme/studio/ThemeStudioPopupEditing.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/theme/studio/ThemeStudioButtonIcons.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/theme/studio/ThemeStudioStatusBar.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/theme/studio/ThemeStudioTextStroke.kt`

提交：`536d9e67`、`54c5554a`、`4334e021`、`a8c95ceb`、`a26c052d`、`0c2856e3`、`1f72a906`、`edfcd776`、`359228b7`、`9011d9dd`

### 主题交换与旧资源整理

状态：confirmed。主题传输保留自定义外观字段，可以选择目标及预览；替换图片和字体时整理不再被使用的旧资源。

入口：主题导入导出；主题工作室 → 更多

升级边界：需要迁移工作室素材、源码宜使用工程包或文件快照。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/management/databackup/themes/ThemeTransferEntry.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/databackup/themes/ThemeTransferViewModel.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/theme/ThemeSettingsContentEditor.kt`、`app/src/main/java/com/ai/assistance/operit/data/backup/RawSnapshotResourceReferences.kt`

提交：`39fb3dbc`、`ad642967`、`2679206a`、`1f5ec3b8`

### 模型设置工作区

状态：confirmed。同页选择配置并在连接、参数、总结、子代理、功能五个标签切换。可设置上下文长度、总结触发和规则，查看实际执行总结的模型，支持时选择思考深度。

入口：设置 → 模型配置

升级边界：原独立总结页归入“总结”；规则按当前配置，实际执行模型仍由全局总结功能绑定决定，历史图片和音视频保留回合仍是全局设置。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/ModelConfigScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/model/ModelWorkspaceComponents.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/contextsummary/ModelContextSummaryContent.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/contextsummary/ContextSummaryDraft.kt`

提交：`49db947c`、`4e025277`、`51c84e5b`、`e81dec5c`

### 模型分组与连接反馈

状态：confirmed。配置可分组、排序和移组，选择器可分组过滤；导入保留有效分组。切换配置会取消旧的模型列表查询；错误能提示连接或 DNS 原因。新配置提高默认上下文，Gemini 获取模型列表采用配置的服务地址，打开设置不再顺便改写思考档位。

入口：设置 → 模型配置 → 顶部配置选择器／连接／参数

升级边界：删除分组需确认；新建默认值不应当作已存配置被统一修改。不同接口的能力仍不同。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/ModelConfigScreen.kt`、`app/src/main/java/com/ai/assistance/operit/data/preferences/ModelConfigManager.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/sections/ModelApiSettingsSection.kt`、`app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/GeminiProvider.kt`

提交：`d6ff5d5b`、`15d48262`、`6527548b`、`ef388580`、`488f7252`、`aa8fb9ae`、`63953157`、`5062f6de`、`0a4507bb`、`dbe7bf93`

### 语音服务工作区

状态：confirmed。朗读、识别、唤醒三页集中管理语音档案、连接、语言音色和规则，可搜索或手动填写选项，也可直接试听、测试识别。

入口：设置 → 语音服务

升级边界：沿用原档案；在线 STT 档案不管后台唤醒，后台仍使用本地 Sherpa NCNN；无效 JSON 或正则不会保存。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/SpeechServicesSettingsScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/components/speech/workbench/SpeechServicesWorkbench.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/components/speech/workbench/SpeechSynthesisTestSection.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/components/speech/SpeechRecognitionTestSection.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/components/speech/workbench/SpeechProfileDraft.kt`

提交：`a0dac38a`、`92a321bd`、`541a1a14`

### 语音条录制、试听和转写

状态：confirmed。聊天可发可播放的语音条，或使用 STT 文字输入。发送音频时支持的对话模型先接收原音频，其后可用音频识别功能模型和当前 STT；已拒绝的音频不会反复重发。显示的转写保留原话，可靠声音观察只作为独立请求上下文。

入口：聊天输入区的录音入口；模型配置 → 功能 → 音频识别；语音服务 → 识别 STT

升级边界：在线语音方式可能向配置的服务上传录音；语音通话与语音条不同。

源码：`app/src/main/java/com/ai/assistance/operit/core/chat/voice/VoiceAudioRequest.kt`、`app/src/main/java/com/ai/assistance/operit/core/chat/voice/VoiceAnalysis.kt`、`app/src/main/java/com/ai/assistance/operit/core/chat/voice/VoiceMessageMarkup.kt`、`app/src/main/java/com/ai/assistance/operit/core/chat/voice/VoiceHoldGesture.kt`、`app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/QwenAIProvider.kt`

提交：`44d4b928`、`ccafa6f2`、`4d657807`、`3a232219`、`28f97ea9`、`c56058e4`、`4a24be74`、`d556dfcc`

### 朗读与角色发言

状态：confirmed。按消息结构提取正文后再套用朗读清理规则；不把已过滤的显示区块当成正文。角色发言的显示清理和语音输出分开。

入口：消息菜单 → 朗读；角色卡 → 发言；语音服务 → 朗读

升级边界：可用现有规则试听核对，不承诺任一设备或服务从此不会中断。

源码：`app/src/main/java/com/ai/assistance/operit/ui/floating/voice/MessageSpeechPlayback.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/components/character/speech/CharacterSpeechSettings.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/WaifuModeSettingsScreen.kt`

提交：`8e2ea297`、`421f5b20`、`8643aa96`、`64b6d6c5`

### 记忆空间与图谱工作台

状态：confirmed。有空间总览、最近记忆、空间增删改与默认空间选择。进入空间后可在图谱和列表筛选、查看、编辑记忆和关系，也能继续管理文档与文件夹。

入口：设置 → 记忆管理；角色的绑定空间入口

升级边界：沿用空间 ID、关系类型和角色绑定，默认空间不能直接删除；文档导入不是从零新增。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/memory/screens/MemoryOverviewScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/memory/workbench/MemorySpaceOverview.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/memory/screens/MemoryScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/memory/workbench/MemoryLibraryPage.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/memory/workbench/MemoryGraphCanvas.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/memory/workbench/MemoryEditors.kt`

提交：`916345f0`、`ecc46741`、`018f6867`

### 记忆检索和向量模型设置

状态：confirmed。记忆设置分组，向量模型统一跟随功能绑定，语义检索收拢候选，工具检索与设置阈值对齐，结果可带相关度分数。

入口：记忆空间 → 设置／配置向量模型；模型配置 → 功能

升级边界：相关度分数不是事实可信度。已有记忆不因页面搬迁而自动重建。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/memory/workbench/MemoryLibraryPage.kt`、`app/src/main/java/com/ai/assistance/operit/data/preferences/FunctionalConfigManager.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/memory/screens/dialogs/MemorySearchSettingsDialog.kt`

提交：`7fe98fc5`、`cf632dbb`、`15369fec`

### 数据与备份统一入口

状态：confirmed。“备份恢复、聊天管理、诊断日志”三页集中使用文件快照、数据库备份、单项导入导出和聊天管理；顶部概览可进入用量统计。

入口：设置 → 数据与权限 → 数据与备份

升级边界：原入口路由转到对应页签。文件快照、数据库备份和单项交换用途不同；锁定保护仍适用。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/management/databackup/DataAndBackupScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/ChatBackupSettingsScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/ChatHistorySettingsScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/main/screens/OperitScreens.kt`

提交：`083b1d1b`

### 文件快照恢复媒体与主题引用

状态：confirmed。快照携带使用中的图片、字体和主题资源，导入时重新挂接媒体与主题脚本引用；导出可选择是否带日志。

入口：数据与备份 → 备份恢复 → 文件快照

升级边界：不带日志只影响快照，不清设备日志。快照仍可能含聊天、配置等敏感内容，不是脱敏文件。

源码：`app/src/main/java/com/ai/assistance/operit/data/backup/RawSnapshotBackupManager.kt`、`app/src/main/java/com/ai/assistance/operit/data/backup/RawSnapshotMediaRestore.kt`、`app/src/main/java/com/ai/assistance/operit/data/backup/RawSnapshotResourceReferences.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/ChatBackupSettingsScreen.kt`

提交：`757cceec`、`2679206a`、`1f5ec3b8`、`76b22679`

### 聊天导出与可恢复归档

状态：confirmed。恢复聊天导出并支持逐条读取 JSON 消息；聊天 CSV 适合查看、交换，Operit 聊天归档用于迁移聊天结构，也可处理关联的子代理树。

入口：数据与备份 → 聊天管理／备份恢复；聊天管理的归档动作

升级边界：统计 CSV 已撤下，不影响聊天 CSV。CSV 不是可恢复完整聊天的归档。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/ChatHistorySettingsScreen.kt`、`app/src/main/java/com/ai/assistance/operit/data/converter/ChatHistoryCsv.kt`、`app/src/main/java/com/ai/assistance/operit/data/chat/archive/OperitChatArchive.kt`、`app/src/main/java/com/ai/assistance/operit/data/repository/ChatHistoryManager.kt`

提交：`0caccba4`、`2411554c`、`20a6d822`、`d8844ce8`、`7ea432f3`、`b759dab1`、`30fae461`

### 轻量首次启动与独立用户协议

状态：confirmed。首次欢迎页点击开始即可进入，不要求先配置模型、授权或在启动页确认协议。完整本地化协议可从关于页查阅。

入口：首次启动；设置 → 关于 → 用户协议

升级边界：已经完成旧版协议引导的用户会迁移到已完成状态，不需重新操作。

源码：`app/src/main/assets/templates/android/app/src/main/java/com/java/myapplication/MainActivity.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/support/onboarding/screens/WelcomeScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/support/agreement/screens/AgreementScreen.kt`、`app/src/main/java/com/ai/assistance/operit/data/preferences/AgreementPreferences.kt`、`app/src/main/java/com/ai/assistance/operit/ui/main/screens/OperitScreens.kt`

提交：`73ed1570`、`5fa6de67`、`9e18fca7`、`ee0c0070`、`9d89cc34`

### 工具授权与系统设置入口

状态：confirmed。权限列表跟随应用语言及已启用的工具，各级权限提供系统设置入口，实际执行按已授予能力及规则判断；模型不能自行跳过授权检查。

入口：设置 → 数据与权限 → 工具权限／系统权限

升级边界：权限仍由用户控制，不应把某级描述成允许全部工具。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/ToolPermissionSettingsScreen.kt`、`app/src/main/java/com/ai/assistance/operit/core/chat/tools/ToolExecutionManager.kt`、`app/src/main/java/com/ai/assistance/operit/core/tools/permissions/PermissionCapabilityResolver.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/support/demo/components/PermissionLevelCard.kt`

提交：`41a2af8c`、`5e9e6e57`、`6e02bd19`、`59b146a1`、`f9cda429`

### 诊断日志可过滤后导出

状态：confirmed。可排除调试或系统内容、仅保留错误上下文、隐藏可识别的敏感内容、去掉时间戳；可暂停日志写入和清空日志。

入口：数据与备份 → 诊断日志

升级边界：过滤默认关闭且只影响导出，分享前仍需检查敏感内容。暂停写入不是永久关闭。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/management/databackup/logcat/LogExportOptions.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/databackup/logcat/LogExportFilter.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/databackup/logcat/LogcatScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/databackup/logcat/LogcatViewModel.kt`

提交：`76a6e2a1`、`7af42208`、`083b1d1b`

### 按类别管理存储

状态：confirmed。可从数据与存储页分析占用，分别管理 Linux、离线模型、记忆、扩展、聊天和备份等类别。

入口：设置 → 数据与权限 → 数据与存储

升级边界：备份删除后影响恢复，离线模型删除后可能需要重新下载；清理前核对范围。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/management/storage/LocalModelStorageScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/storage/MemoryLibraryStorageScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/main/screens/OperitScreens.kt`

提交：`91e83aa0`、`fe979335`、`6724fa99`、`2b028c81`、`bc12842b`

### 横竖屏的重载设置

状态：confirmed。屏幕方向变化时可选自动重新加载、每次询问或不处理。

入口：设置 → 显示 → 屏幕方向变化时

升级边界：这是处理选项，不是对所有显示问题的修复承诺。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/GlobalDisplaySettingsScreen.kt`、`app/src/main/java/com/ai/assistance/operit/data/preferences/OrientationReloadPolicy.kt`、`app/src/main/assets/templates/android/app/src/main/java/com/java/myapplication/MainActivity.kt`

提交：`a2931437`

### 用量统计的四页与活动日历

状态：confirmed。从总览、模型、配置、请求四页看用量与估计费用，可按日期、模型、配置、来源、状态等筛选并追溯单次记录。活动图支持日、周、月、年及累计，并有独立日历。

入口：设置 → 数据与备份 → 顶部用量统计

升级边界：日看今天，周月年是截至昨天的滚动窗口，累计含今天；旧汇总可能没有单条明细。统计 CSV 不再提供。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/models/tokenstats/TokenUsageStatisticsScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/models/tokenstats/TokenStatsOverviewContent.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/models/tokenstats/TokenStatsDetailDialogs.kt`、`app/src/main/java/com/ai/assistance/operit/data/stats/TokenStatsInsightsService.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/models/tokenstats/TokenActivitySection.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/models/tokenstats/ActivityDateRangePolicy.kt`

提交：`99a65571`、`b77cfcda`、`cf719e4f`、`9efa89d9`、`7b9d5942`、`6af59142`、`067e7208`

### 高级计价与旧配置处理

状态：confirmed。支持基础、分段、峰谷时段等计价规则。删除模型配置不删除历史用量；已删除配置不再列作可编辑配置。清空用量有延迟确认。

入口：用量统计 → 配置／清空用量

升级边界：费用为估算，需核对币种、汇率与单位；旧配置解绑或字段缺失不等同服务商实际账单。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/models/tokenstats/TokenUsageStatisticsScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/models/tokenstats/TokenStatsDialogs.kt`、`app/src/main/java/com/ai/assistance/operit/data/stats/TokenStatsSettingsManager.kt`、`app/src/main/java/com/ai/assistance/operit/data/stats/TokenUsageRepository.kt`、`app/src/main/java/com/ai/assistance/operit/data/stats/TokenCostCalculator.kt`

提交：`9e32da6f`、`b55fa1b5`、`9d5a80f6`、`dc522c71`、`d136c363`、`55d052c8`

### Antigravity 登录入口

状态：confirmed。模型连接页提供 Antigravity 登录入口。

入口：设置 → 模型配置 → 连接 → Antigravity

升级边界：正式 1.12.2 无该入口；审计 HEAD 有对话框及服务分派，外部登录、账户资格和服务可用性未经验证。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/sections/ModelApiSettingsSection.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/models/antigravity/AntigravityLoginDialog.kt`、`app/src/main/java/com/ai/assistance/operit/api/chat/AIServiceFactory.kt`

提交：`a7676fa3`、`3ff9a2a7`

### 用编辑工具按目标调整外观

状态：confirmed。Operit Editor 可查询当前角色、角色卡和角色组的主题字段及版本，并按字段增量修改配色、字体、头像、背景和消息外观；另有独立的角色发言设置命令。

入口：Operit Editor 的主题／角色发言命令

升级边界：编辑命令不等于完整主题工作室；语言、全局显示、输入区、侧栏和部分玻璃设置仍在设置页。自定义主题脚本可覆盖偏好的实际显示。

源码：`app/src/main/java/com/ai/assistance/operit/core/tools/commands/theme/ThemeSettingsTools.kt`、`app/src/main/java/com/ai/assistance/operit/core/tools/commands/waifu/WaifuSettingsTools.kt`、`docs/TODO/feat/editor-theme-controls/index.md`

提交：`0b37185d`、`3feb7159`

### 崩溃后的本地报告

状态：confirmed。崩溃报告改为独立整理错误详情、堆栈与日志导出，提供重新启动入口。

入口：应用捕获崩溃后显示的本地报告

升级边界：报告不会因为显示而自动发送；选择导出后分享前核对日志。它不是 2.0 首次实现崩溃恢复。

源码：`app/src/main/java/com/ai/assistance/operit/ui/error/report/CrashReportScreen.kt`、`docs/TODO/feat/data-and-backup/index.md`

提交：`083b1d1b`、`5d234fb6`

### 额度或限流触发密钥池切换

状态：confirmed。已有 API Key 池在额度用尽、限流等失败且有其他可用密钥时可立即切换，健康时结合近期状态和已用密钥选择；冷却及恢复情况在密钥池中更清楚。只有一把密钥时，适用的错误仍保留正常重试。

入口：设置 → 模型配置 → 连接 → API Key 池

升级边界：并非所有错误都会重试；切换需要可用备选。自定义端点和模型配置级自定义请求头是既有机制，不是本版新增的每密钥 Header。

源码：`app/src/main/java/com/ai/assistance/operit/api/chat/keypool/ApiKeyPoolSelector.kt`、`app/src/main/java/com/ai/assistance/operit/api/chat/keypool/ApiKeyRetryPlanner.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/keypool/ApiKeyPoolSettingsSection.kt`、`app/src/main/java/com/ai/assistance/operit/api/chat/AIServiceFactory.kt`

提交：`ffbed687`、`8de92874`、`a5a1d744`

### 思考质量滑块的粒子反馈

状态：confirmed。在已有思考质量滑块上，新增长短随档位变化的粒子动画与主题颜色，并修正最高档容易回弹的问题。

入口：聊天输入区 → 思考质量；设置 → 主题 → 自定义颜色

升级边界：粒子是视觉反馈而非耗时或额度指示。模型实际支持的思考档位不同；滑块本身不是新增。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/modules/input/common/ThinkingQualitySlider.kt`、`app/src/main/java/com/ai/assistance/operit/data/preferences/ThemePreferenceSnapshot.kt`

提交：`ee036296`、`a662fc88`

### DeepSeek 图片可通过 Files API 引用

状态：confirmed。启用图片能力的 DeepSeek 连接可在发送前尝试上传图片到 Files API，Chat Completions 和 Responses 均可使用返回的 file_id；上传失败时仍可尝试以内嵌图片发送。

入口：设置 → 模型配置 → 连接 → DeepSeek → 图片能力；聊天中发送图片

升级边界：依赖端点和所用模型支持，附件可能上传到配置的外部服务。此功能和已移除的 DeepSeek 服务端联网搜索开关无关；不宣称有独立用户文件管理界面。

源码：`app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/DeepseekProvider.kt`、`app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/DeepseekFileUploader.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/sections/ModelApiSettingsSection.kt`

提交：`dd508f13`、`d01554fc`、`f3c916df`、`86773d0e`、`fe7714cf`

### 请求质量和计费精度优化（待 #282 合入）

状态：pending-merge-282。对完整筛选范围接通平均首字延迟、P95 完整耗时、已定价请求均费、成功请求平均 TPS，并给出各自样本数；支持计费倍率、小额费用显示及缓存读取率和成功率分级。

入口：用量统计 → 总览／请求详情／计价设置

升级边界：当前正式 HEAD 不含此提交。无有效样本显示破折号，历史缺失字段不计入；倍率默认 1，支持按 Token 和按次计费。

源码：`app/src/main/java/com/ai/assistance/operit/data/stats/TokenStatsInsightsQuality.kt`、`app/src/main/java/com/ai/assistance/operit/data/stats/TokenStatsInsightsService.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/models/tokenstats/TokenStatsPricingPresentation.kt`、`app/src/main/res/values/token_stats_quality_pricing.xml`、`docs/TODO/fix/token-statistics-quality-pricing/01-runtime-quality.md`

提交：`5bed6bf1917635785ed9ef9e8c4537450f7221f6`

## 工具、工作区与资源

### 分仓发行与完整 APK 升级

状态：feature。正式版和增量补丁统一在 Kong 独立发行仓，离线资源另设仓库。首次安装或从 1.12.2 系列升级到 2.0.0 时使用完整 APK。

入口：Operit-Kong-patch Releases；Operit-Kong-resources Releases

升级边界：正式 1.12.2 包名为 com.ai.assistance.operit；Kong 正式构建为 com.ai.assistance.operit.kong，不能原位覆盖，Android 不自动迁移应用数据。先从旧版导出备份，再安装 Kong 并按支持范围导入核对；不要先卸载旧版。已使用同包名、同签名的 Kong 且版本号递增时可覆盖；临时调试签名不行。最低 Android 8.0（API 26，和基线相同）。

源码：`app/build.gradle.kts`、`ci/distribution/updates/README.md`、`app/src/main/java/com/ai/assistance/operit/data/updates/distribution/DistributionReleaseResolver.kt`

提交：`eed0b0bb688658db60ab6b9c215d68686fdf9c0b`、`292443425feaa62b5d558c0645c0d13f14ee2a2c`、`93503038d45ba89bb61c32cbb65b9ef801b9a038`

### 正式版与 Beta 更新渠道

状态：feature。关闭 Beta 计划时只检查正式版；开启后也检查带 +数字的补丁版。下载文件和增量还原会校验摘要。

入口：设置 → 更新检查／Beta 计划

升级边界：退出 Beta 不主动降级。增量只适合同基础版本，且原 APK 必须匹配；重新签名或重新打包的 APK 不能作为原增量基线。

源码：`app/src/main/java/com/ai/assistance/operit/data/updates/UpdateManager.kt`、`app/src/main/java/com/ai/assistance/operit/data/updates/UpdateSourceDownloader.kt`、`app/src/main/java/com/ai/assistance/operit/data/updates/PatchUpdateInstaller.kt`、`ci/distribution/updates/README.md`

提交：`292443425feaa62b5d558c0645c0d13f14ee2a2c`、`ff0b0f73a038f591be905e8e549bfc436168b3cc`、`e0f8f17105beed8aa869e5738aeef802b50cd117`、`e677a2a632414aead5de89062daf386618043533`

### 离线资源按需下载、导入与管理

状态：feature。Ubuntu 安装归档、内置离线语音资源和 Android／Windows 打包模板从安装包拆出，按需下载。离线资源页可按类型管理和导入、导出支持的备份，也可管理本地语音模型。

入口：设置 → 离线资源；相关功能也可进入所需安装流程

升级边界：新安装先准备所需资源再离线使用。同应用身份升级可继续使用已有 Ubuntu；删除安装归档与删除已安装 Ubuntu 是两件事，删除后者前应备份。正在使用的离线模型不能直接删除。

源码：`app/src/main/java/com/ai/assistance/operit/data/resources/OfflineResources.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/resources/hub/ResourceHubViewModel.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/resources/hub/ResourceHubScreen.kt`

提交：`6dc1dbfae6e96c2d3f48a4b29f916180125a02e3`、`541a1a144c6fcaf807c15e6ccce228250e4d5295`、`94a5313f739c652edd631e48c3a70e2a89877bad`、`eec3472073216ce34e83fa1d2082939802200c42`、`e12804ea13a2b01e72999c02392bc343b29e44ce`、`6808131a88bf2759fc6d614a6de9aa44c8220b7d`

### 内置扩展按需交付

状态：feature。在市场查看内置扩展详情，按需安装或更新，也可从内置资源管理入口维护。更新检查、进度和结果分别反馈。

入口：插件市场的内置条目；离线资源管理

升级边界：不等于全部扩展随 APK 预装；同身份升级保留已有安装和版本状态，避免重复导入或降回较旧资源。实际可下载条目及版本取决于发布资源清单。

源码：`app/src/main/java/com/ai/assistance/operit/data/resources/extensions/BuiltInExtensionResources.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/extensions/packages/market/BuiltInMarketDetailScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/extensions/packages/market/ExtensionUpdates.kt`

提交：`5d0c465657b634782c27646f150784a2142cfcd8`、`67de0cb5450addea0d7c1a43ccd83f357dc05b64`、`b69aa717fc4340bc862a7344184474f2d0cfc518`、`aa03c52e3f7280acf4494228fd307fd8cdb2ac1b`、`c3d0588e2a9161623922e6d9af59778612ea1d10`、`884ef0fe2d3631ab36d7719a3bda10366868a52d`

### 工具权限从五档收拢为三种模式

状态：feature。普通、管理员、Root 按设备实际授权提供能力。权限页会反映已安装且启用的工具并提供系统设置入口；模型不能自行跳过工具授权。

入口：设置 → 工具权限；角色卡的工具可见性仍独立

升级边界：旧同身份数据中的 ACCESSIBILITY／DEBUGGER 偏好迁至 ADMIN。选择管理员不自动授予无障碍或 Shizuku；Root 也不替代系统授权。配置迁移不是跨 Android 包名搬家。

源码：`app/src/main/java/com/ai/assistance/operit/core/tools/permissions/PermissionOperationRouter.kt`、`app/src/main/java/com/ai/assistance/operit/core/tools/permissions/AndroidPermissionLevel.kt`、`app/src/main/java/com/ai/assistance/operit/data/preferences/AndroidPermissionModeMigration.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/toolpermissions/ToolPermissionPage.kt`

提交：`718707fd19ab7c27e38c8807055c80809d25e6dc`、`5e9e6e571390ddc24541c59b9c1c2975ad841b2a`、`6e02bd19d926c023af4f9066db2bcaf06670b3ec`、`59b146a1cc4eb852394878686690bc44fc536abc`、`f9cda4296182df809bd6df29a17374ba73914e98`、`41a2af8c0d52841f281e0dedb475660fcdbd6615`

### 文件工具正确处理授权目录和 Linux 搜索

状态：fix。支持递归复制系统授权目录，修正嵌套路径、复制进自身以及失败时留下不完整目标的问题；修正部分 Linux 搜索路径和行号。

入口：Agent 文件工具；工作台中的系统授权目录或 Linux 文件

升级边界：授权目录需在 Android 系统选择器授权；Linux 路径属于所选环境。

源码：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/filesystem/SafFileSystemTools.kt`、`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/filesystem/LinuxFileSystemTools.kt`

提交：`b4295499836a8b050b12c0929cc29512325f359a`、`5ff31674399cc3328860b1d43d1b1679988919a6`、`6b42c451c83d8442b6fc16de58ce32fd2f2676e3`、`92dd0fa40bba278a1f8a12c592080d6d8107d27e`、`94b57556eb638f8244918a5980f608139779f7d7`、`b56bea3a6418e02574d302167787f89d4dd8a378`

### 网页读取有明确的超时与拦截原因

状态：fix。网页工具可设整次、加载与读取时限，遇到网络失败或明确访问拦截时返回原因；浏览器会话工具会核实点击目标和导航结果，减少误报成功。

入口：Agent → visit_web；交互操作使用内置浏览器会话工具

升级边界：默认整次 30 秒且含排队，支持等待懒加载。读取入口不是登录、点击、填表或精确读取 HTTP 接口响应的入口；明确拦截不能只靠延长时间解决。

源码：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/StandardWebVisitTool.kt`、`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/WebVisitOptions.kt`、`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/WebVisitBlocking.kt`、`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/StandardBrowserSessionTools.kt`

提交：`74fd5c394190d1b2ca4f9a3a05db25383a930968`、`fe6cfb40aefc56414456f22e5c7d47243d978533`、`52090b1cf4add37913e2232dc5ab15f1bbe76b27`、`7acc5472e17813bca420b9d88e51a2677114be3d`

### HTTP 的 timeout 限制整次请求

状态：fix。内置 HTTP 工具执行整体超时，并允许另外约束连接、读取和写入阶段。

入口：Agent → http_request；包内 HTTP 请求

升级边界：整体 timeout 是秒，timeout_ms 是毫秒别名。外部 HTTP 聊天服务入口属另一项。

源码：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/StandardHttpTools.kt`

提交：`f21f0ae25107c6752a192e6195f387f2ec4c4dfa`、`c9597e411a1efee74ef3af71e6ebf9b4ca79ba3e`、`e054b23f3d0a0359f52f8050b8fa7eb3aaee1457`、`a1db67cb030c9282208bd3310a5fcdcd627c99da`

### 外部 HTTP 聊天改由终端管理

状态：feature。移除旧设置页；自带终端可以查询状态、启停服务、管理 Token，可在启动时打开自动登录的浏览器。

入口：web-chat start-server --login；web-chat status；web-chat stop-server

升级边界：保存的端口、Token 与启用状态在同身份升级中继续使用；HTTP Chat、Web Chat 和 A2A 共用服务。仅在可信网络下开启，不分享 Bearer Token 或自动登录链接；必要时重置 Token。

源码：`docs/doc-src/feature-protocol/web_chat_cli.md`、`docs/doc-src/feature-protocol/external_http_chat.md`、`app/src/main/java/com/ai/assistance/operit/integrations/http/ExternalChatHttpServer.kt`

提交：`0285ae32a01b3adb2a7785d61f9d2a764627af5e`、`421f5b208f2623c4e437533719885233709630d7`、`b56bea3a6418e02574d302167787f89d4dd8a378`

### PhoneAgent 支持主屏控件观察与接管

状态：feature。满足条件时可根据当前界面的控件观察结果定位、点击或输入；执行阶段和点击反馈更清楚。用户接管后丢弃旧控件定位，继续时需重新确认。

入口：手机助手／UI 自动化的进度与接管控件

升级边界：本地控件观察限主屏，需实际授权并启用无障碍；虚拟屏仍是独立路径并有对应 Shizuku／Root 能力要求。不是所有应用都提供可读取的控件。

源码：`app/src/main/java/com/ai/assistance/operit/core/tools/agent/PhoneAgent.kt`、`app/src/main/java/com/ai/assistance/operit/core/tools/agent/PhoneAgentLocalSession.kt`、`app/src/main/java/com/ai/assistance/operit/ui/common/displays/PhoneAgentUiStatus.kt`

提交：`6cce30bb46dbe97ba4780b4e2937bdc2935d5202`、`0e66c813da4f2585ecf27c79612e5fc90fc3328a`、`cba45d358fa9b998d9b9949725853a3cfa2437ee`、`9c5be2719adeb78144191883d196384fb6b21111`、`d6cf7b94bfa45f7fd10bf3db171776da2407f1d8`、`a7cd8ed5520c76ad637378ba95a921a1d55fe8f5`、`061bedbbb37c53949e3276fe6cf37d4ea5260aa3`、`35e26aac5cd86f127f1cc0736b38588b26785342`、`080fead02ab709cf7a78ba77c68b02b100ab5fc5`、`f53afd73118a3926fb5f755272caeeacddbd6d81`

### 对话内的原生 A2UI 互动卡片

状态：feature。模型能在对话展示表单、表格、图表等原生卡片并更新数据。等待型表单可将结构化提交直接交还给正在等待的本轮模型。

入口：当前对话中让模型生成 A2UI；开发者用 a2ui_catalog 查看支持范围

升级边界：不需要外部示例引导；该引导已移除。等待提交依赖活动的流式对话；工具授权及角色可见性仍适用。A2UI 与扩展的 Compose DSL 是不同入口。

源码：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/a2ui/A2uiTools.kt`、`app/src/main/java/com/ai/assistance/operit/ui/common/a2ui/A2uiCard.kt`、`app/src/main/java/com/ai/assistance/operit/ui/common/a2ui/A2uiActions.kt`

提交：`2c9212ae3bfb6122fdaa342375ba214929f5c744`、`b2d7e30741ad40e59c252d8678aa949c929401b9`、`7f2bbfefc3a129f34b3e07a382bf13e701d095f8`、`93f36a639ee90920e510e3754ad10ac43261343b`、`ac86467c446da4971f7594fe0b5fb9cec55fb472`、`2d21ab4baf955a17dbf5a30e5a4a4e3432c04304`、`88cf4f27d5551326c96ac3dd517039aba3316b54`、`582ea78f58569c8b7fe0d432c0152f0203db23af`、`26599b5d3a9a69d7ddc1a8706fb4d8c8a090825b`、`294e2cb8cbb16eef87677f8a3172db6d58982bfd`

### 扩展分类与完整详情

状态：feature。插件、工具包、Skills、MCP 分类管理；可从列表进入详细信息、配置及适用的导入、更新和删除功能。禁用的扩展不能再通过详情页或直接运行脚本继续执行。

入口：扩展管理 → 插件／工具包／Skills／MCP → 点条目

升级边界：ZIP 导入与线上市场是不同入口。删除会按真实安装来源处理；先核对条目和可能受影响的数据。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/extensions/packages/screens/PackageManagerScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/extensions/packages/screens/PackageDetailEntryScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/extensions/packages/screens/PackageDetailsScreen.kt`、`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/runtime/ToolPkgManager.kt`

提交：`ce64910f4210492b0b478a263efc85bb389a3801`、`0a1860feb7146da45a5b8a38491e3b63f27f7bfe`、`3dd12589cae8374379179f4eafb5a77c7daabe4f`、`baff7720bde8f34621c3a7573590796b91ae2179`、`6b4f94d9d18769e20f1a6ac7f468e8ff40bc215a`、`f8434861ca0f6a9eccb506aee7a98d0f04272988`、`070b71a213a331dd13e90da4c3a7dffec6a1a0ee`、`bd9ddba7a67a7512bef0a89ecd81401c6933acb7`

### 插件市场下载进度与创作者中心

状态：feature。市场下载显示进度、速度和剩余时间，更新提供过程和完成反馈；创作者中心集中发布和管理入口。

入口：插件市场 → 获取／更新；创作者中心

升级边界：默认不限制于精选。市场核对 ToolPkg 声明的宿主 API 范围；不兼容时应使用匹配的应用／扩展版本。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/extensions/packages/screens/UnifiedMarketScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/extensions/packages/screens/UnifiedMarketManageScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/extensions/packages/screens/UnifiedMarketDetailEntryScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/extensions/packages/market/ExtensionUpdates.kt`

提交：`0eb5674a381fe0a0a1152fcb561dbc0da5fd15b0`、`7c72bf2a3d5f327139e9f4db976b16b5ebb81815`、`bae1ed5b0617335bb7bb2781695fc1a8ce82fa75`、`c3d0588e2a9161623922e6d9af59778612ea1d10`、`44bead0362d1ed6b2452469eb79c43115d6c95bb`、`c05ca059821d1b44bc1322f650ce3aa4f6091b59`、`a15fb38ca8210dae4575516cfbc3a02b923927ff`

### ToolPkg 与 Compose DSL 开发资料

状态：feature。补齐包格式、宿主 API、Hook、界面组件、类型和示例；开发 Skill 使用更新后的脚本和参考文档。插件页面加载与返回聊天草稿的衔接有修复。

入口：SandboxPackage_DEV；docs/SCRIPT_DEV_GUIDE.md；plugins/docs/toolpkg_developer_documentation

升级边界：ToolPkg 和 Compose DSL 在 1.12.2 已存在，不作为 2.0 首次推出。作者应更新开发类型并核对 API 兼容范围。

源码：`docs/SCRIPT_DEV_GUIDE.md`、`plugins/docs/toolpkg_developer_documentation/06_ui_and_compose/compose_dsl.md`、`app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsComposeDslBridge.kt`、`app/src/main/java/com/ai/assistance/operit/ui/common/composedsl/ToolPkgComposeDslScreen.kt`

提交：`f1bbcd80b927bdbda50d07483322a805b1bfe432`、`c86235ea9bf783303f25c0a88c9438665d51e770`、`832c8abd316ec34c33e1ebcf7613434d0ddc5222`、`e949eb5eff3a7b9e651a3108b97563e4c79ff771`、`bae1ed5b0617335bb7bb2781695fc1a8ce82fa75`

### 脚本与 MCP 参数及失败反馈

状态：fix。显式 MCP 字符串及 JS 的可选参数类型保留；脚本日志与最终结果区分，包激活、超时或 JSON 参数错误会报告失败。MCP stdio 启动失败有更具体的诊断。

入口：工具包执行结果；MCP 配置与启动

升级边界：不要把执行日志当结果 JSON；依赖旧隐式参数转换的脚本应复核。已配置 MCP 静默加载不等于所有服务自动启动。

源码：`app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsTools.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/extensions/packages/screens/MCPConfigScreen.kt`、`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/runtime/ToolPkgManager.kt`

提交：`f0bffa78e3f280cc3c3b170ab4d8897acc025c48`、`9d053ef08171192f76280444e6c7e292b0b21f74`、`7e539475da231322218f9e78374ffccfef944ccd`、`b60e94e33f1bf084866c630a998924aedf56fa6e`、`319d1b6e5f319c37d672b745b67a210dc79dea9b`、`fc74dbf78a939f1c77a858d2abefe2c79e4b44d5`、`e9cf4f826183772099afc27650d6c8b0101d4e2d`、`9de59d2444dea4848abf049731e57a4bfce0a770`

### 终端长任务可启动、查询和精确取消

状态：feature。自动化工具可把命令作为有 ID 的任务启动，查询输出尾部与当前画面并取消该任务；保留旧直接执行入口。

入口：super_admin 的 terminal_start、terminal_poll、terminal_cancel

升级边界：一次查询等待结束不会终止任务；总执行期限包含排队。输出和画面是替换快照，不要反复拼接。非零退出按失败返回，保留可用输出；取消后按结果判断会话是否存活。

源码：`app/src/main/java/com/ai/assistance/operit/core/tools/system/terminal/tasks/TerminalTaskRegistry.kt`、`app/src/main/java/com/ai/assistance/operit/core/tools/system/terminal/OperitTerminalManager.kt`、`app/src/main/java/com/ai/assistance/operit/core/tools/TerminalTaskResultData.kt`、`modules/terminal`

提交：`6ff74671b0e7206529ad45d21a84936c886dadaf`、`0fc56cbb0f7c46c026d58721c6b6bbfc8a1e05b5`、`2230a7c95d3e64fda9546c248ad3a38dfc3b2d33`、`76336d49b1c8766e7cada4a34b5e88ac2014ddcf`、`988ad7afdb9b82ce1d9529acdd3d7f3a4e4500c4`

### 终端交互、键盘和命令清理

状态：fix。修正部分键盘视口和光标问题，补齐终端状态及光标位置回复，改善依赖这些回复的 CLI 提示。Root 单次命令按进程隔离并限制时限，修正 Shizuku 因输出管道堵塞导致的等待。

入口：工作台 → 终端；Root／Shizuku 系统命令工具

升级边界：隔离执行不是文件沙箱，仍具所授予的权限，取消不会撤销已发生的副作用。不能据此称所有终端卡顿已修。

源码：`modules/terminal`、`app/src/main/java/com/ai/assistance/operit/core/tools/system/shell/IsolatedShellCommand.kt`、`app/src/main/java/com/ai/assistance/operit/core/tools/system/shell/RootShellExecutor.kt`、`app/src/main/java/com/ai/assistance/operit/core/tools/system/shell/ShizukuShellExecutor.kt`

提交：`a576507ed3cde27e51b887e5423fa4125421f21e`、`9a89e4585ddec55d369c82b60173d2d2755606cc`、`0cffc3b55d8ed4e0b4a10adf651ef77cc560981d`、`ff0b0f73a038f591be905e8e549bfc436168b3cc`、`d63e72b2f0d9b4184152b7f0960ac34c8280aa25`、`faf330100000174c76da70eb8189c2ea6aafa53d`、`e001799b1361912f5c588eb98d30a6811d517e06`、`3d92ee2bcc0cdaf596d5e2901d55f44dff3f3e29`

### Ubuntu 内置 Codex 风格的 apply_patch

状态：feature。终端支持通过参数、补丁文件或标准输入应用补丁，支持只检查、预览差异、防止新增或移动时覆盖、JSON 输出。

入口：新开的本地 Ubuntu 终端 → apply_patch --help

升级边界：现有本地 Ubuntu 无需重装；新会话初始化命令，不安装到 SSH 远端。默认不禁止覆盖，并非原子操作；部分失败时此前修改可能已写入。与 APK 增量还原脚本无关。

源码：`tools/native_apply_patch/README.md`、`tools/native_apply_patch/src/cli/options.rs`、`tools/native_apply_patch/install.sh`、`modules/terminal`

提交：`35396470127a4ee7d192e5c1089dc5ea2f862af1`、`432c36a61f7eb18fd4723f4dc11671a5c166e69c`、`4510d853e516232f688f5af04b29718bb5e3706b`、`ba2a64e3e5da43e9845f3d7a33dd2a87092d33c7`

### 工作台统一工作区、终端与浏览器

状态：feature。一个主页收拢创建或选择工作区、打开文件、手动终端和浏览器会话。工作台与浮窗复用浏览会话，无需绑定目录也能打开终端和浏览器。

入口：侧栏 → 工作台

升级边界：原工具箱独立文件管理器入口移除，集中到工作台。使用系统授权目录时仍须授权；Linux 环境和 SSH 会话有各自配置。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/workspace/hub/WorkspaceHubScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/workspace/hub/WorkspaceHubHome.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/workspace/hub/WorkspaceBrowserController.kt`

提交：`6801b64355cd751462265d36ebff70f20a74a8d9`、`b6fbe8d5a5cb29e3ce62a3a16586c09a93d83f15`、`c793a3ae27b3ed87f5d7cd5890e839aa96ad205b`、`c8f4ca9e7309e7c403d970ba17b972bf2bf6d16f`、`fb8c496633fe825472e9d2b3b8ddc1813a029fb6`、`7acc5472e17813bca420b9d88e51a2677114be3d`、`f26751f375e642c9c193de71117df89ade48947c`

### 工作台文件浏览、搜索、选取和预览

状态：feature。路径可直接编辑，支持收藏、设置浏览首页、过滤和递归搜索；可横滑多选并按范围复制、剪切、删除、分享。支持从工作台查看文件属性、预览和打开文件。

入口：工作台 → 文件浏览；聊天文件链接也可进入查看

升级边界：复制与删除提供进度；手动隐藏只影响显示，并不删除文件。系统选择器打开文件适用于受支持的来源和类型。大文本有读取限制，不能宣称未定位的长时间读取卡住已修。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/workspace/WorkspaceScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/workspace/browser/WorkspaceSearchEngine.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/workspace/browser/WorkspaceBrowserOperations.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/workspace/preview/WorkspaceHtmlPreview.kt`

提交：`6801b64355cd751462265d36ebff70f20a74a8d9`、`035f319989191323e22344f18a79dcaf55028ea3`、`e24a30d9ae773828827d2de1d2cb752771496a51`、`6f239395bd0febe5c27853b1c1e88a382be605f1`、`f96fc2da194d48c36c95aa0436d28aeff8fafb99`、`a9dd831752956dd68fb2edb1395683e47f8a08ff`、`9b9bb75d93b96b72204067fa576db30e5279d960`、`d9a9b8366809e2b4ad383ece19f22c47ca567fd5`、`fb8c496633fe825472e9d2b3b8ddc1813a029fb6`、`d2d786c207fb4a241ac621c0265be5f7d6929b5a`

### 归档预览、解压和 ZIP 修改

状态：feature。可查看 ZIP、7z、RAR、TAR 等归档及常见压缩变体，按需打开、解压选中或全部内容；可创建 ZIP、7z、TAR、TAR.GZ，ZIP 可带 AES 密码。普通可编辑 ZIP 可加文件、删条目和改名。APK 可先查看信息并交由系统安装。

入口：工作台 → 选择压缩包或 APK；文件操作 → 压缩／解压

升级边界：修改带签名的 APK、JAR 等容器会使原签名失效；打开包内文件到编辑器不代表保存时自动写回归档。非 ZIP 或嵌套归档的浏览是只读，不能保证所有 RAR 分卷或加密变体。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/workspace/archive/WorkspaceArchiveModel.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/workspace/archive/WorkspaceArchiveEngine.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/workspace/archive/WorkspaceArchiveExtraction.kt`、`docs/TODO/feat/workspace-file-management/05-archives-and-file-routing.md`

提交：`fb8c496633fe825472e9d2b3b8ddc1813a029fb6`

### 文本编辑、离线高亮与 Git 代码溯源

状态：feature。编辑器提供查找替换、跳行、撤销重做、软换行、另存为、只读与外部变化检测；扩充离线 TextMate 高亮并支持自定义规则。代码溯源可查当前行的 Git 作者、来源提交和文件差异。

入口：工作台 → 打开文本；编辑器更多 → 代码溯源

升级边界：软换行只改变显示。代码溯源需适用的 Git 环境，未提交行、无 Git 或不支持的文件有不同提示；系统授权目录不运行此 Git 溯源。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/workspace/editor/CodeEditor.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/workspace/editor/EditorSettingsScreen.kt`、`docs/doc-src/dev-core/editor-highlighting/editor-highlighting.md`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/workspace/git/WorkspaceGitBlameRepository.kt`

提交：`2a3164b07d203a58d5734a07df63b9ffdff79000`、`fb8c496633fe825472e9d2b3b8ddc1813a029fb6`、`eb14e2101e71363374cb26875dbdbf0544b2b58f`、`9bed5d694cdb3fff2a972b735c2a3838fad2f3c5`、`2b7bf8e3dabbf0cd52faa712edf90b909a402a59`、`04387e4d9676503afcc9f642d89d66454fa0c59b`

### 工作区文件变更与对话产出审阅

状态：feature。查看 Git 工作树、分支和已采集的上一轮／历史变更，支持单栏或双栏差异、路径筛选、逐段展开并定位到文件；可清理记录而不删除当前文件。

入口：工作台 → 文件变更／对话产出；有记录才显示产出入口

升级边界：只对当前对话明确绑定的工作区目录采集，不从命令所在位置、文件目标或默认路径推导工作区；只记录所支持后端与范围内的变化。Git 比较只读，不会提交、推送或撤销。二进制和超过快照限额的变化可能无文本差异；升级前无采集历史不能倒推。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/workspace/changes/WorkspaceChangesScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/workspace/changes/WorkspaceFileMonitor.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/workspace/changes/WorkspaceRecordingScope.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/workspace/changes/WorkspaceChangeRecorder.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/conversation/workspace/changes/WorkspaceChangeRecordActions.kt`

提交：`fb8c496633fe825472e9d2b3b8ddc1813a029fb6`、`5ac91cfdf971ec786c7ea9cbcb815a7ba62ec5c5`、`53825ab6a56013218d674880461b654f8ceb1c22`、`f26751f375e642c9c193de71117df89ade48947c`、`04387e4d9676503afcc9f642d89d66454fa0c59b`、`8995de3f905c592a6ac686af3d97458829cd9bc5`、`cb31d5f2f084f0e0c7f076f0d9e2deaa2535b143`、`0a5bb1c82adb241328e57f75584d5f13a37ba739`、`e8596182f7f325a7adaea73f868491b9b55d8309`、`134e421ed49f98b7a63886d13bdd1debfea7bf3f`、`b8b1649c0f05f1a2be78c20bddc70e2fae231064`、`51763fd7f4db02c36f5d3607a29322529b905226`

### 静态 HTML 项目改用终端打包

状态：feature。自带 Ubuntu 终端的 build html 可将准备好的静态页面打包为 Android APK 或 Windows 应用。

入口：新开的本地 Ubuntu 终端 → build html --help

升级边界：独立 HTML 打包页／原工作台入口移除；先准备匹配的离线模板。原聊天内容导出不是本命令，也未因此删除。

源码：`docs/doc-src/feature-protocol/html_build_cli.md`、`app/src/main/java/com/ai/assistance/operit/core/htmlbuild/HtmlAppBuilder.kt`、`app/src/main/java/com/ai/assistance/operit/core/htmlbuild/HtmlBuildCommandServer.kt`

提交：`fb8c496633fe825472e9d2b3b8ddc1813a029fb6`、`4c45381649ab961b3aae05d6675d432bb41a0bde`、`ec5705882e91455156e31954f2095dd99d77271e`

### 工具可在宿主内管理应用设置

状态：feature。使用 operit_editor 时新增权限模式、可用性能指标及范围明确的设置命令；主题草稿修改可安全合并受支持字段。

入口：Agent → operit_editor；开发者见命令参考

升级边界：修改依然由宿主授权；不是为模型自动提升权限。避免把 JavaScript 代理逻辑移到宿主的纯内部迁移列成独立卖点。

源码：`docs/OPERIT_EDITOR_COMMANDS.md`、`app/src/main/java/com/ai/assistance/operit/core/tools/commands/PlatformCommandRegistry.kt`、`app/src/main/java/com/ai/assistance/operit/core/tools/commands/PlatformCommandDispatcher.kt`

提交：`a752eecefac71ad4d3f15427ead50937c065110e`、`56e01b03d00cf43c0d582b41f2cc59d5ece18155`、`a57550c852a82c91900c10904fc51904de06f832`、`0b37185dac42299684234394d8798e2e29ae234c`、`3feb7159fbcf2c63b9461bbcc782426ac27f11fb`

### 扩展运行和权限诊断维护

状态：maintenance。修正部分脚本任务收尾、临时引擎释放和同类失败重复显示；应用升级时一起维护底层组件。

入口：扩展与系统工具执行

升级边界：生成文件重排不列用户功能；后续 #284 的 API／镜像图标迁移安全归并到维护项，本审计不把它当成新终端功能。

源码：`app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsTools.kt`、`app/src/main/java/com/ai/assistance/operit/core/tools/system/terminal/TerminalCommandFlow.kt`

提交：`780c5849a315d1c5ffc6e5a266e83a9827bf50a0`、`cf83c5e16d4d6ccdb73fa61ac8b3a11dabb9498c`、`988ad7afdb9b82ce1d9529acdd3d7f3a4e4500c4`、`3fbad1f4e0e7f298d98d012aa2aa30a33df4dd66`

### 数据与存储管理

状态：feature。按类别查看 Operit 管理的存储占用，进入各类别处理历史、媒体、扩展、Linux、本地模型或备份；可集中清理支持的缓存与浏览器 Cookie。

入口：设置 → 数据与存储

升级边界：清理 Cookie 可能需要重新登录；删除媒体、扩展数据或 Linux 环境前先核对范围并备份。缓存清理不是全量数据迁移。

源码：`app/src/main/java/com/ai/assistance/operit/data/storage/DataStorageRepository.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/storage/DataStorageScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/storage/BackupExportStorageScreen.kt`

提交：`91e83aa0b53db34c86b9adc7e131d3bad0be958a`、`e3b9a6f8c6902c544aabd9d1fff505580cb7e3c0`、`fe979335088f6f07076412b021ccc6ac18279cbd`、`6724fa99ea9bbad4ecdaec474c0cc81fef57efc1`、`2b028c815a77b461ba619999a9f22824c6d646bd`、`418b92d1494b0ec70d62df0b56ae6f653b46c570`、`04435e63e16b1ad091dd7d449d53898af6230300`

### 工作流复制、JSON 迁移与失败定位

状态：feature。工作流可复制，支持 JSON 导入导出；运行失败时显示具体节点、动作和原因。

入口：工作流列表 → 复制／导入；工作流详情 → 导出 JSON／运行记录

升级边界：导入的工作流默认禁用，检查动作、配置和依赖后再启用；导出前检查可能包含的敏感值。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/automation/workflow/screens/WorkflowListScreen.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/automation/workflow/screens/WorkflowDetailScreen.kt`、`app/src/main/java/com/ai/assistance/operit/core/workflow/runtime/WorkflowFailureDescription.kt`

提交：`8f317107dc073ba64c6309f236920f30bf192caa`、`b1d7bc628cfbe685f318ca31d1cfea8a3aeadf4b`

### 日志导出可过滤及隐藏已识别的敏感信息

状态：feature。导出时可排除调试或系统日志，只保留错误上下文，隐藏可识别的敏感内容或去掉时间戳。

入口：日志页面 → 导出选项

升级边界：过滤选项默认关闭，原始日志不因导出被改写。隐藏敏感内容不是完全脱敏，分享前仍需检查。

源码：`app/src/main/java/com/ai/assistance/operit/ui/features/management/databackup/logcat/LogExportOptions.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/databackup/logcat/LogExportFilter.kt`、`app/src/main/java/com/ai/assistance/operit/ui/features/management/databackup/logcat/LogcatScreen.kt`

提交：`76a6e2a197454c7752f6538d0f225f8d12bc6475`、`7a42aed7fb0a6c6129d665a9c0af89342c884691`

### 图像工具包支持带参考图生成和 GIF

状态：feature。OpenAI、通义千问、硅基流动和 SpaceXAI 工具包可通过本地图片或公开 URL 提供参考图。OpenAI 工具包支持 GPT Image 2.6 的 GIF 格式和可选帧数、每帧时长。修正远程参考图转为文件上传及较长上传的超时。

入口：扩展管理 → 对应图像工具包 → 生成图像；也可在聊天中调用

升级边界：使用前安装或更新相应工具包，并配置支持该功能的服务端点、模型与凭据；智谱工具包仍仅文生图。程序会把参考图交给配置的服务，不建议上传含敏感信息的图。GIF 帧选项只用于 GIF，普通图保持留空。不据本仓源码保证外部资源已发布。

源码：`plugins/examples/openai_draw.ts`、`plugins/examples/qwen_draw.ts`、`plugins/examples/siliconflow_draw.ts`、`plugins/examples/spacexai_draw.ts`、`plugins/examples/zhipu_draw.ts`、`app/config/builtin-extensions.json`

提交：`14c8afdc3bfb3bb22caa824d164bb49b57d7bff1`、`ef1bf4b6c307f9d64e16912fc8ac256d14d57f15`、`29d678120994ab535ddb80bda171a4fa77b4f1bc`、`6ce3cbba78b21c96392e0ed9c731d498d634df8f`、`f969706fa9905cc98b55f3736ccac3f1a2d50648`、`8b5ee894f75382734f0ae16737b286b3719ec594`、`78d5c9c87fb8f4917f00da105adb34a99d5b9202`

### Code Runner 隔离执行与收尾

状态：fix。多次运行使用各自的执行上下文和临时目录，避免并发覆盖文件或把前一个命令的目录留给后续脚本；限制清理等待并回收异常的隐藏 shell。

入口：Code Runner 工具包

升级边界：多语言执行在 1.12.2 已有，本次未新增语言；已有 Python 虚拟环境和 Node 依赖工作区仍沿用。命令隔离不等于安全沙箱，也不证明原先低概率卡住现象已消失。

源码：`plugins/examples/code_runner.ts`、`docs/TODO/fix/code-runner-execution-isolation/index.md`、`app/src/main/java/com/ai/assistance/operit/core/tools/system/terminal/OperitTerminalManager.kt`

提交：`e001799b1361912f5c588eb98d30a6811d517e06`、`f308ecd7825e21171ef1265f19f373b4125e5bcf`、`3d92ee2bcc0cdaf596d5e2901d55f44dff3f3e29`

[DONE] 分模块条目与最终源码对应已记录，待合入项保持独立。
