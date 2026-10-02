---
title: 声明与文档覆盖索引
status: in_progress
---

# 声明与文档覆盖索引

此页跟踪 `examples/types/` 中的每个声明入口和其对应参考页。迁入旧 API 页不代表逐方法语义审查已经完成；仅当签名、字段、版本和宿主行为均已核对后，页面才可标记为完整。

## 声明文件映射

- `index.d.ts`：全局导出面；对应[全局运行时 API](../03_runtime/global_api.md)、[QuickJS 全局运行时](../03_runtime/quickjs_runtime.md)与本索引。
- `core.d.ts`：核心调用、结果基础类型和 NativeInterface；对应[核心模块参考](../04_modules/core.md)、[全局运行时 API](../03_runtime/global_api.md)及[Java Bridge](../07_types_and_libraries/java_bridge.md)。声明中的重载、NativeInterface 方法和已确认差异已逐项记录；`_` 与 `dataUtils` 的实现边界仍按全局页说明。
- `tool-types.d.ts`：工具名/参数/返回映射；对应 `04_modules/tool-types.md`，逐工具覆盖待审。
- `toolpkg.d.ts`：注册、Hook、IPC、WASM；对应[注册 API](../03_runtime/registry.md)及[Hook 参考](../05_hooks/index.md)。注册 API 的 UI/导航、Hook、聊天菜单和 AI Provider 字段、默认值、函数引用归一、关联资源校验及失败行为，以及 `readResource`/`getConfigDir`、IPC、本地/跨上下文路由、WASM 参数归一与结果解码均已按运行时核对。
- `results.d.ts`：宿主工具结果类型；对应 `04_modules/results.md`。162 个接口名现均在参考页中出现，38 个 `BaseResult` 包装到 `data` 类型的映射已逐项核对；22 个此前未列出的结果数据接口已有字段表。运行时逐字段核对目前为 136 个接口/671 个声明字段：此前的 Chat/Memory/CharacterCard 核对加上已审计的 11 个模型配置结果接口/108 个字段、28 个工作流结果/节点接口/82 个字段、UI/Shell/Intent/Terminal/FFmpeg/字符串接口 16 个/76 个字段、文件/网络接口 16 个/81 个字段、本轮系统/设备/应用结果 22 个/86 个字段，以及软件设置/沙箱/MCP/语音服务结果 13 个/104 个字段。本轮还记录 `SpeechServicesUpdateResultData.sttApiKeySet` 当前误读 TTS key 的运行时映射差异。其余 26 个接口/71 个字段尚未完成同等运行时核对。
- `android.d.ts`：Android facade 和类；对应 `04_modules/android.md`，全部类方法需逐项对照。
- `chat.d.ts`：Chat facade；对应 `04_modules/chat.md`。`Chat.call()`、`ChatCallOptions` 为 API `1.0.1`。
- `files.d.ts`：Files；对应 `04_modules/files.md`，方法级覆盖待审。
- `network.d.ts`：Net；对应 `04_modules/network.md`，方法级覆盖待审。
- `okhttp.d.ts`：OkHttp Bridge；对应 `04_modules/okhttp.md`，方法级覆盖待审。
- `system.d.ts`：System；对应 `04_modules/system.md`，方法级覆盖待审。
- `software_settings.d.ts`：SoftwareSettings；对应 `04_modules/software_settings.md`。26 个公开方法均已列出 TypeScript 签名；环境变量、沙箱/MCP、speech 更新、角色卡字段校验与工具访问解析、模型配置创建/更新/删除、参数归一、函数绑定/索引与连接测试行为已有源码核对。`ModelConfigUpdateOptions` 缺少 6 个实现支持的 `llama_*` 更新字段、`SpeechServicesUpdateResultData.sttApiKeySet` 错取 TTS key 两项差异已记录。
- `memory.d.ts`：Memory；对应 `04_modules/memory.md`，方法级覆盖待审。
- `workflow.d.ts`：Workflow；对应 `04_modules/workflow.md`。10 个 `Runtime` 方法、CRUD/patch/节点和连接解析、启停与错误结果语义，以及 `WorkflowExecutor` 的触发选择、依赖调度、边条件、失败分支、Condition/Logic/Extract/Execute 节点执行行为均已按源码核对。
- `tasker.d.ts`：Tasker；对应 `04_modules/tasker.md`，方法级覆盖待审。
- `ui.d.ts`：UI/UINode；对应 `04_modules/ui.md`，方法级覆盖待审。
- `ffmpeg.d.ts`：FFmpeg；对应 `04_modules/ffmpeg.md`，方法级覆盖待审。
- `cryptojs.d.ts`：CryptoJS；对应 `04_modules/cryptojs.md`，方法级覆盖待审。
- `jimp.d.ts`：Jimp；对应 `04_modules/jimp.md`，方法级覆盖待审。
- `compose-dsl.d.ts`：Compose DSL runtime、节点工厂和 props；对应[Compose DSL 参考](../06_ui_and_compose/compose_dsl.md)，需继续核对全部 renderer 字段和 WebView/Canvas 行为。
- `compose-dsl.material3.generated.d.ts`：生成的 Material 3 组件；对应[Material 3 组件参考](../06_ui_and_compose/material3_components.md)，registry 已列全，props 到宿主行为仍待审。
- `java-bridge.d.ts`：Java/Kotlin Bridge；对应[Java Bridge 参考](../07_types_and_libraries/java_bridge.md)，主要 facade 已核对，所有值转换/重载分支仍待审。
- `material-icons.d.ts`：Material Icon 名称与 registry；对应[Material Icons](../06_ui_and_compose/material_icons.md)，字面量集已列入。
- `pako.d.ts`：Pako compression API；对应[内置库与压缩接口](../07_types_and_libraries/libraries.md)，已记录声明/实现差异。
- `quickjs-runtime.d.ts`：QuickJS runtime globals；对应[QuickJS 全局运行时](../03_runtime/quickjs_runtime.md)，timer、Storage、console 与 performance 已对照兼容层。ToolPkg metadata 参数转换不属于 QuickJS globals，参数类型、缺省/必填检查与转换失败路径另见包格式参考，并按 `JsToolManager` 实现核对。

## 明确的 API 版本标注

当前声明中标有 `@since ToolPkg API 1.0.1` 的公开能力包括：

- ToolPkg 注册：`registerChatMessageMenuItem`、`registerChatRuntimeHook`。
- 聊天菜单和运行态 Hook：对应 event name、sender、slot/state enum、handler、event payload、return/dialog 类型和 registration 类型。
- Chat 模块：`ChatCallOptions`、`Chat.call(options)`；结果为 `ChatCallResultData`。
- 结果类型：`ChatCallFinishReason`、`ChatCallResultData`。
- Compose DSL：`DialogProperties`、`AlertDialogProps`、`DialogProps`、`AlertDialog` 工厂、`Dialog` 工厂。
- NativeInterface 同步声明：`registerToolPkgChatMessageMenuItem`、`registerToolPkgChatRuntimeHook`。

宿主最低版本要求按[版本规则](./api_versions.md)单列：ToolPkg API `1.0.1` 要求 Operit `1.12.1+4` 或更新版本。`@since` 注释、类型存在和运行时逐方法门禁是三件不同的事；只有 `JsToolPkgApiRuntime` 明确包装的成员会在调用时执行 facade 版本校验。

## 已确认的声明/运行时差异

- `ChatMessageEventPayload` 的 sender/variant 字段以声明为准，实际值来自 `ChatMessage` 模型；逐字段差异待接口审计阶段核对。
- `pako.inflate` 声明接受可选 options 和 `Uint8Array`，实现却要求 `{ to: "string" }` 且只接受字符串；native 解码 raw DEFLATE 后按 UTF-8 返回文本，native 错误包装文案也有偏差。
- Compose `useMemo(key, factory, deps?)` 声明了 `deps`，当前 bridge 只按 key 首次缓存，不比较依赖项。
- `dataUtils.stringifyJson(value)` 声明返回 string，但顶层值序列化结果为 `undefined` 时 wrapper 也会返回 `undefined`；抛出异常时才回退为 `"{}"`。
- `Icons` runtime 是返回属性名字符串的 Proxy，并不校验 `KnownMaterialIconName` 枚举。
- `NativeInterface.registerImageFromBase64()` 和 `registerImageFromPath()` 的当前成功路径返回空字符串，未返回声明注释所描述的图片 link；失败路径返回错误文本。
- `NativeInterface.logDebug()` 在当前 `JsEngine` 中为空实现。
- `NativeInterface.javaLoadDex()`、`javaLoadJar()`、Java 实例/字段调用等 bridge envelope 的失败字段在运行时为 `message`，而 `core.d.ts` 的部分注释描述为 `error`。
- `DateResultData` 的 `.d.ts` 声明为 `date: Date`、`formattedDate`、`timestamp`；Android 数据类实际序列化 `date: string`、`format`、`formattedDate`，并没有 `timestamp`。
- `BluetoothDeviceData.type` / `bondState`、`BluetoothScannedDeviceData.source`、`BluetoothBleCharacteristicData.properties` 和 `MusicPlaybackResultData.state` 在 `.d.ts` 使用字面量联合类型；对应 Kotlin DTO 使用 `String` 或 `List<String>`，运行时并无这些联合类型的枚举约束。
- `ModelConfigResultItem` 的 Kotlin DTO 额外序列化 `apiProviderTypeId`，该字段缺失于 `.d.ts`；`apiProviderType` 与额外字段都映射同一个 provider ID。`ModelConfigConnectionTestOutcome` 和结果字段在 Kotlin 侧为字符串，实际由实现写入小写 `passed` / `unverified` / `failed`。
- `HttpResponseData` 的 Android DTO 还声明 `contentBase64` 与 `cookies`，缺失于 `.d.ts`；`cookies` 默认空映射，是否省略默认值取决于序列化配置。`Link`、`FileEntry`、`GrepLineMatch`、`GrepFileMatch` 在 `.d.ts` 为顶层接口，在 Kotlin 中分别表现为访问网页/列目录/Grep DTO 内的嵌套数据类。
- `GrepResultData.filePattern?` 在 `.d.ts` 被声明为结果字段，但 Kotlin DTO 不返回该字段；它只作为搜索工具输入过滤条件使用。Grep 的匹配分组、计数还受工具结果上限影响。
- `FilePartContentData` 的当前 Linux 实现把 `startLine`/`endLine` 表示为 0 起始左闭右开范围；`partIndex`/`totalParts` 保留为兼容字段，当前值固定为 `0`/`1`。
- `SimplifiedUINode.shouldKeepNode?()` 在 `.d.ts` 中看似可调用，但 Kotlin 实现为 `private` 辅助方法；它只参与 `toTreeString()` 的节点过滤。
- `IntentResultData.type` 在 `.d.ts` 中声明为活动/广播/服务联合类型，但当前 Android DTO 没有该字段；`action`、`uri`、`package_name`、`component`、`flags`、`extras_count`、`result` 才是当前序列化字段。
- `FFmpegResultData` 的 `.d.ts` 顶层 `videoStreams` / `audioStreams` 与 `FFmpegStreamInfo` 结构未在当前 Kotlin DTO 中实现；运行时使用可选 `outputFile`、`mediaInfo`，并在 `mediaInfo` 下返回 `StreamInfo`。
- `.d.ts` 的 `AutomationExecutionResultData` 对应源码类名 `AutomationExecutionResult`；其 `executionError` 与 `finalState` 在运行时为 nullable 字段，声明中的可选标记不能单独推断 JSON 字段一定缺省。

## 完成标准

- [ ] 逐个 `.d.ts` 文件列出全部公开值、函数、类方法、事件、参数对象和结果字段。
- [ ] 每个公开方法对应一个有行为说明的参考条目；不能只给类型链接。
- [ ] 每个 `@since` 字段/方法/类型标注 API 版本，并与宿主最低 Operit 版本分开记录。
- [ ] 每项关键错误、空值、超时、取消、缓存、并发和副作用均有实现依据。
- [ ] 记录未实现、未暴露、声明不一致和无法由实现证实的项目。
- [ ] 新文档内所有相对链接有效；旧文档继续保留，删除工作另行决定。