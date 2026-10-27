# 应用源码包职责

本文记录原 API 117 个源文件整理后的包布局。源码根前缀为 com.ai.assistance.operit，api 顶层名称保持现状。目录整理表达现有职责，业务算法与执行流程保持原值。

## 模型访问与语音

```text
api/
	chat/
		AIService.kt
		AIServiceFactory.kt
		EnhancedAIService.kt
		AIForegroundService.kt
		llmprovider/
		keypool/
		decorator/
		request/
		model/
		protocol/
		transport/
		error/
		runtime/
	speech/
		stt/
			http/
		tts/
		audio/
		wake/
		runtime/
```

AIService 与 AIServiceFactory 是模型访问接口和装配入口。llmprovider 放提供方协议适配及专用辅助，keypool 管理密钥与会话，decorator 包装服务，request 管理并发、速率与重试参数，model 负责目录、能力与思考配置，protocol 处理模型工具调用协议，transport 构造地址与 TLS 配置，error 表达请求失败，runtime 装配功能模型服务。

speech/stt 中的 SttService 与 SttServiceFactory 提供识别和转写入口；speech/tts 中的 TtsService 与 TtsServiceFactory 提供合成和播放入口。audio 放共享录音、VAD 与预录缓冲，wake 放唤醒和声纹，runtime 放 STT/TTS 共用的 Sherpa 会话。

EnhancedAIService 保留现有 Java 桥完整名称，AIForegroundService 保留 Android Service 组件名。它们暂留 api/chat 是本轮明确的兼容边界。

## 对话、记忆与插件桥接

```text
core/
	chat/
		context/
		runtime/
		protocol/
		tools/
		filebinding/
		hooks/
		media/
		voice/
	memory/
		extraction/
plugins/
	toolpkg/
		ai/
```

对话的上下文、轮次运行状态、消息标记、工具执行、文件绑定、输入 Hook、媒体标记与音频输入分别放在 core/chat 的对应职责包。模型协议的工具调用转换仍在 api/chat/protocol，与对话消息标记区分。

ConversationMemoryExtractor、MemoryAutoSaveScheduler、ChatMemoryRebuildManager、ChatMemoryRebuildTimeScope 与 ChatMemoryWindowPlanner 放在 core/memory/extraction，负责从对话提取、重建和调度记忆；实际数据存取继续使用现有 MemoryRepository。

ToolPkgJsAiProviderService、TokenUsage 与 ToolPkgMainHookRunner 位于 plugins/toolpkg/ai，提供 JavaScript 模型适配桥接。

## 变更边界与审查

旧、新路径、五处类名调整、兼容边界与静态核对方式见 [API 包迁移记录](../../TODO/chore/package-layout/index.md)。对应单元与设备测试的包路径随职责调整，测试内容保持原值。

namespace、applicationId、Android 组件、配置枚举、DataStore 键、JSON 字段与已确认的 Java 桥入口均沿用现有定义。根包品牌、util、UI/data、Widget、DocumentsProvider 和其他源码目录的职责审查属于后续工作。