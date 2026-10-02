---
title: ToolPkg 注册 API
status: draft
---

# ToolPkg 注册 API

`ToolPkg` 是 ToolPkg 主入口运行时注入的注册对象。注册调用在主模块求值阶段收集为 JSON 定义，宿主完成解析后才把模块、Hook 和 Provider 接入对应运行时。它不是 `Tools` 命名空间，也不是直接执行内置工具的入口。

**版本约定：** 下文未单独注明的注册方法以 `ToolPkg.Registry` 的 API `1.0.0` 基线为准。消息长按菜单与聊天运行态 Hook 从 ToolPkg API `1.0.1` 起提供。Operit 宿主最低版本见[版本规则](../09_compatibility/api_versions.md)。

## 主入口执行约束

- 主入口需要导出 `registerToolPkg()`；宿主在注册会话中执行该函数并捕获注册定义。
- 注册调用需要发生在该会话开启期间；会话关闭后再调用 native registration 会报 `toolpkg registration session is not active`。
- 注册定义必须是可序列化 JSON 对象；带函数的字段由运行时解析为导出的模块函数引用。不能序列化成稳定函数引用的函数会在注册时抛错。
- 带 `screen` 的 UI 注册要求 screen 能解析为包内路径；`ComposeDslScreen` 函数引用会被归一化成模块路径。
- 注册函数只负责声明。运行时执行、资源读取和 WASM 调用要在后续适当的调用上下文中完成。

## UI 与导航注册

### `ToolPkg.registerToolboxUiModule(definition)`

```ts
registerToolboxUiModule(definition: ToolboxUiModuleRegistration): void
```

- **API 版本：** `1.0.0` 基线
- **用途：** 注册可从工具箱打开的 UI 模块。
- **`id`：** 包内稳定标识；宿主将其与容器包名组合用于解析注册项。
- **`runtime?`：** 运行时标记；应与目标模块实现匹配。当前类型未限定字面量，具体支持值由 UI 运行时决定。
- **`screen`：** Compose DSL screen 函数或包内模块路径。注册桥会把函数引用规范化为序列化路径。
- **`params?`：** 传给 screen 的 `ToolParams` 初始参数。
- **`title?`：** 字符串或按语言代码映射的本地化标题。
- **`keepAlive?`：** 请求保留页面实例；是否保留仍受对应 UI 容器生命周期控制。

### `ToolPkg.registerUiRoute(definition)`

```ts
registerUiRoute(definition: UiRouteRegistration): void
```

- **API 版本：** `1.0.0` 基线
- **`id`：** route 注册标识。
- **`route?` / `routeId?`：** 路由别名字段；不要在同一条定义中给出互相矛盾的值。
- **`runtime?`：** UI runtime 标识。
- **`screen`：** screen 函数或包内模块路径，必须能被宿主序列化和加载。
- **`params?`：** screen 初始参数。
- **`title?`：** 本地化标题。
- **`keepAlive?`：** 页面存活策略提示。

### `ToolPkg.registerNavigationEntry(definition)`

```ts
registerNavigationEntry(definition: NavigationEntryRegistration): void
```

- **API 版本：** `1.0.0` 基线
- **`id`：** 导航项稳定 ID。
- **`surface`：** 当前声明值为 `toolbox` 或 `main_sidebar_plugins`，决定导航项出现位置。
- **`route?`：** 点击后打开的已注册路由。
- **`action?`：** 导航动作 Hook；若提供函数，必须可以解析为持久化模块函数引用。
- **`title?`、`icon?`、`order?`：** 本地化标题、图标名和同 surface 内排序值。
- **动作事件：** `navigation_entry_action`；payload 字段见[Hook 事件索引](../05_hooks/index.md)。

### `ToolPkg.registerDesktopWidget(definition)`

```ts
registerDesktopWidget(definition: DesktopWidgetRegistration): void
```

- **API 版本：** `1.0.0` 基线
- **`id`：** 小组件注册 ID。
- **`route?` / `routeId?`：** 点击打开的 route。
- **`render?` / `renderRouteId?`：** 小组件本体对应的 UI route；不填写时宿主可按 `route` 解析展示路由。
- **`title?`、`subtitle?`、`description?`：** 本地化展示文本。
- **`icon?`、`order?`：** 图标和列表排序。
- 注册桥当前把定义序列化后交给宿主；路由存在性和桌面容器行为由 widget 宿主校验。

## Hook 注册方法

以下方法都在模块注册期提交定义；回调签名和返回值并不相同，不能共享一个“通用 hook 返回值”假设。每个 Hook 的触发点、阶段、payload、数据合并和错误语义见[Hook 参考](../05_hooks/index.md)。

| 方法 | API 版本 | 定义字段 | 宿主效果 |
| --- | --- | --- | --- |
| `registerAppLifecycleHook` | 1.0.0 | `id`、`event`、`function` | 订阅指定 application/activity 生命周期事件 |
| `registerMessageProcessingPlugin` | 1.0.0 | `id`、`function` | 参与消息处理/回复接管链 |
| `registerXmlRenderPlugin` | 1.0.0 | `id`、`tag`、`function` | 处理指定 XML tag 的渲染请求 |
| `registerInputMenuTogglePlugin` | 1.0.0 | `id`、`function` | 提供输入菜单开关定义并响应开关事件 |
| `registerChatInputHook` | 1.0.0 | `id`、`function` | 观察编辑事件并拦截/替换提交 |
| `registerChatViewHook` | 1.0.0 | `id`、`function` | 观察聊天视图打开、更新和关闭 |
| `registerChatMessageHook` | 1.0.0 | `id`、`function` | 收到消息持久化通知，返回值不改写已保存消息 |
| `registerChatMessageMenuItem` | **1.0.1** | `id`、`title`、`icon?`、`order?`、`senders?`、`function`、`dialog?` | 在消息长按菜单添加动作项，可打开 Compose DSL 弹窗 |
| `registerChatRuntimeHook` | **1.0.1** | `id`、`function` | 订阅聊天运行状态变化 |
| `registerToolLifecycleHook` | 1.0.0 | `id`、`function` | 观察工具调用请求、权限、执行和结果阶段 |
| `registerPromptInputHook` | 1.0.0 | `id`、`function` | 修改 Prompt 输入处理阶段数据 |
| `registerPromptHistoryHook` | 1.0.0 | `id`、`function` | 修改正式 Prompt 历史准备阶段数据 |
| `registerPromptEstimateHistoryHook` | 1.0.0 | `id`、`function` | 修改 token 估算使用的历史数据 |
| `registerSystemPromptComposeHook` | 1.0.0 | `id`、`function` | 修改系统提示词组合阶段数据 |
| `registerToolPromptComposeHook` | 1.0.0 | `id`、`function` | 修改工具提示词和可用工具列表 |
| `registerPromptFinalizeHook` | 1.0.0 | `id`、`function` | 修改模型请求发送前的最终 Prompt 数据 |
| `registerPromptEstimateFinalizeHook` | 1.0.0 | `id`、`function` | 修改 token 估算的最终 Prompt 数据 |
| `registerSummaryGenerateHook` | 1.0.0 | `id`、`function` | 修改摘要提示词或生成结果 |

以上版本号来自类型声明的 `@since` 标记和 ToolPkg namespace 基线；运行时的逐方法门禁只对版本规则页列出的 facade 方法执行。

## AI Provider 注册

### `ToolPkg.registerAiProvider(definition)`

```ts
registerAiProvider(definition: AiProviderRegistration): void
```

- **API 版本：** `1.0.0` 基线
- **`id`：** Provider 稳定 ID。
- **`displayName?`、`description?`：** UI 展示文本；运行时会校验供统计映射使用的 ID 与名称非空。
- **`listModels.function`：** 返回 `{ models: Array<{ id, name }> }`。
- **`sendMessage.function`：** 返回 `{ text, usage? }`；`usage` 可包含 `input`、`cachedInput`、`output` token 数。
- **`testConnection.function`：** 返回 `{ success, message?, error? }`。
- **`calculateInputTokens.function`：** 返回 `{ tokens }`。
- 四个回调都必须放在 `{ function }` 字段中，并能映射到已导出的模块函数；回调收到 provider 专属事件及 `AiProviderConfig`，参数和返回形状见 `ToolPkg.AiProvider*` 类型。
- Provider 每个方法可返回值或 Promise；其具体并发、请求超时和错误映射由 Provider bridge 决定，需结合[AI Provider 专页](../05_hooks/ai_provider.md)阅读。

## 资源与持久配置

### `ToolPkg.readResource(key, outputFileName?, internal?): Promise<string>`

- **API 版本：** ToolPkg `1.0.0` namespace 基线；方法未单独标注 `@since`。
- `key`：`manifest.resources[]` 中声明的资源 key；空字符串会拒绝 Promise。
- `outputFileName?`：输出文件名；宿主会将传入值转为字符串并去除首尾空白。
- `internal?`：只有严格布尔值 `true` 才向 native 层传递内部访问标记。
- 目标包名取自当前调用上下文中的 UI package、`toolPkgId`、container package、subpackage ID 或 package name 参数；没有目标时 Promise 以错误拒绝。
- 成功结果是宿主返回的非空资源路径字符串；找不到资源或 native 返回空路径时 Promise 拒绝。
- 本入口的目标解析和资源访问副作用应结合当前 ToolPkg 调用上下文使用，不应在 `registerToolPkg()` 注册收集阶段读取。

### `ToolPkg.getConfigDir(pluginId?): string`

- **API 版本：** ToolPkg `1.0.0` namespace 基线；方法未单独标注 `@since`。
- 有 `pluginId` 时对其去空白后作为目标，否则从当前调用上下文解析 ToolPkg/package 目标。
- 目标为空或 native 层没有返回非空目录路径时同步抛错。
- 返回路径供插件读写自己的持久配置文件；不要把临时资源释放路径与配置目录混用。

## IPC

### `ToolPkg.ipc.on(channel, handler): () => void`

注册当前 runtime context 的消息处理函数。`handler(payload, meta)` 可同步返回结果或 Promise；返回的函数用于注销本次监听。

`meta` 包含 channel，并可带 `callerContextKey`、`currentContextKey`、`currentRuntime` 和 `packageTarget`。payload/result 的静态默认类型为 `unknown`，调用方应通过泛型明确业务结构。

### `ToolPkg.ipc.off(channel, handler?): boolean`

移除 channel 上匹配的处理函数；省略 handler 时按运行时实现移除该 channel 的监听。返回值表示是否找到并移除了目标监听。应保存原 handler 引用，避免依赖函数源码或新建闭包进行匹配。

### `ToolPkg.ipc.call(channel, payload?, options?): Promise<TResult>`

向目标 runtime context 发起请求并等待响应。`options.targetRuntime?` 指定 `main`、`ui`、`sandbox` 或 `provider`；`targetContextKey?` 指定目标上下文。`IpcMeta` 中的 `packageTarget` 用于标识相关包。channel 名称和 payload schema 由调用双方共同约定；本 API 的类型系统不会自动校验跨包消息结构。

## WASM

### `ToolPkg.wasm.call(moduleId, exportName, args?): Promise<WasmCallResult>`

- **API 版本：** ToolPkg `1.0.0` namespace 基线；方法未单独标注 `@since`。
- `moduleId`：manifest 中声明的 WASM 模块 ID。
- `exportName`：该模块向 ToolPkg 暴露的函数名。
- `args?`：按导出函数参数顺序排列的 tagged values；每个值的 `type` 为 `i32`、`i64`、`f32` 或 `f64`。
- `i32`、`f32`、`f64` 使用 number；`i64` 的入参值使用十进制 string，避免 JS number 精度损失。
- 结果为 number、string、void 或 `WasmResult[]`。结果项含 `type`、`value`，可选 `bits`；`i64` 结果按声明使用 string。
- 注册入口收集期不能执行 WASM 调用；模块、导出函数和 ABI 的有效性由 manifest 解析与宿主 WASM bridge 校验。

相关页面：[包 Manifest 与资源格式](../02_package_model/package_format.md)、[Hook 参考](../05_hooks/index.md)、[API 版本规则](../09_compatibility/api_versions.md)。