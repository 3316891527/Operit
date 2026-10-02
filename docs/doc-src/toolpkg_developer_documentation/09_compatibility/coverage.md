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
- `toolpkg.d.ts`：注册、Hook、IPC、WASM；对应[注册 API](../03_runtime/registry.md)及[Hook 参考](../05_hooks/index.md)。方法和 Hook 页面已按运行时补写，注册字段/错误路径仍需闭环。
- `results.d.ts`：宿主工具结果类型；对应 `04_modules/results.md`。162 个接口、742 个顶层字段签名中，已有 30 个接口的逐字段表，覆盖基础 Chat/ChatCall、Memory 查询/链接和 CharacterCard；其余结果字段仍待核对。
- `android.d.ts`：Android facade 和类；对应 `04_modules/android.md`，全部类方法需逐项对照。
- `chat.d.ts`：Chat facade；对应 `04_modules/chat.md`。`Chat.call()`、`ChatCallOptions` 为 API `1.0.1`。
- `files.d.ts`：Files；对应 `04_modules/files.md`，方法级覆盖待审。
- `network.d.ts`：Net；对应 `04_modules/network.md`，方法级覆盖待审。
- `okhttp.d.ts`：OkHttp Bridge；对应 `04_modules/okhttp.md`，方法级覆盖待审。
- `system.d.ts`：System；对应 `04_modules/system.md`，方法级覆盖待审。
- `software_settings.d.ts`：SoftwareSettings；对应 `04_modules/software_settings.md`，方法级覆盖待审。
- `memory.d.ts`：Memory；对应 `04_modules/memory.md`，方法级覆盖待审。
- `workflow.d.ts`：Workflow；对应 `04_modules/workflow.md`，方法级覆盖待审。
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
- `quickjs-runtime.d.ts`：QuickJS runtime globals；对应[QuickJS 全局运行时](../03_runtime/quickjs_runtime.md)，timer、Storage、console 与 performance 已对照兼容层。

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

- `ToolPkg.ToolLifecycleEventName` 声明没有 `tool_call_intercept`；`ToolPkgToolLifecycleBridge` 在权限检查前同步派发该事件，并按 `{ action: 'block', reason }` 决定是否拦截。执行异常或无效 block 返回会阻断工具调用。
- `ChatMessageEventPayload` 的 sender/variant 字段以声明为准，实际值来自 `ChatMessage` 模型；逐字段差异待接口审计阶段核对。
- `pako.inflate` 声明接受可选 options 和 `Uint8Array`，实现却要求 `{ to: "string" }` 且只接受字符串；native 解码 raw DEFLATE 后按 UTF-8 返回文本，native 错误包装文案也有偏差。
- Compose `useMemo(key, factory, deps?)` 声明了 `deps`，当前 bridge 只按 key 首次缓存，不比较依赖项。
- `dataUtils.stringifyJson(value)` 声明返回 string，但顶层值序列化结果为 `undefined` 时 wrapper 也会返回 `undefined`；抛出异常时才回退为 `"{}"`。
- `Icons` runtime 是返回属性名字符串的 Proxy，并不校验 `KnownMaterialIconName` 枚举。
- `NativeInterface.registerImageFromBase64()` 和 `registerImageFromPath()` 的当前成功路径返回空字符串，未返回声明注释所描述的图片 link；失败路径返回错误文本。
- `NativeInterface.logDebug()` 在当前 `JsEngine` 中为空实现。
- `NativeInterface.javaLoadDex()`、`javaLoadJar()`、Java 实例/字段调用等 bridge envelope 的失败字段在运行时为 `message`，而 `core.d.ts` 的部分注释描述为 `error`。

## 完成标准

- [ ] 逐个 `.d.ts` 文件列出全部公开值、函数、类方法、事件、参数对象和结果字段。
- [ ] 每个公开方法对应一个有行为说明的参考条目；不能只给类型链接。
- [ ] 每个 `@since` 字段/方法/类型标注 API 版本，并与宿主最低 Operit 版本分开记录。
- [ ] 每项关键错误、空值、超时、取消、缓存、并发和副作用均有实现依据。
- [ ] 记录未实现、未暴露、声明不一致和无法由实现证实的项目。
- [ ] 新文档内所有相对链接有效；旧文档继续保留，删除工作另行决定。