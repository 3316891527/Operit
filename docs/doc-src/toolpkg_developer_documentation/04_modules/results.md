# API 文档：`results.d.ts`

`results.d.ts` 是所有工具返回结构的集中定义。它本身不提供运行时方法，但几乎所有 `Tools.*`、`toolCall()` 和部分全局对象最终都会返回这里定义的数据类型。

## 作用

这份文件主要承担两类职责：

- 定义 `...Data` 形式的原始结果结构。
- 定义 `...Result` 形式的包装结果，其中通常包含 `BaseResult` 与 `data` 字段。

## 命名约定

### `...Data`

表示某个工具或能力的返回数据主体，例如：

- `FileContentData`
- `HttpResponseData`
- `UIPageResultData`
- `WorkflowDetailResultData`

### `...Result`

表示带 `success` / `error` 包装的结果对象，例如：

- `SystemSettingResult`
- `UIPageResult`
- `ChatCreationResult`
- `MemoryLinkResult`

### `toString()`

很多 `...Data` 类型都声明了 `toString()`，说明运行时支持把结果转换成可读文本。

## 主要结果分类

### 1. 文件与搜索结果

常见类型：

- `FileEntry`
- `FileExistsData`
- `FileInfoData`
- `DirectoryListingData`
- `FileContentData`
- `BinaryFileContentData`
- `FilePartContentData`
- `FileOperationData`
- `FileApplyResultData`
- `FindFilesResultData`
- `GrepLineMatch`
- `GrepFileMatch`
- `GrepResultData`

其中：

- `FileContentData` 包含 `env`、`path`、`content`、`size`
- `BinaryFileContentData` 通过 `contentBase64` 表示二进制内容
- `GrepResultData` 包含 `matches`、`totalMatches`、`filesSearched`

### 2. 网络结果

常见类型：

- `HttpResponseData`
- `Link`
- `VisitWebResultData`

其中：

- `HttpResponseData` 包含 `statusCode`、`statusMessage`、`headers`、`contentType`、`content`
- `VisitWebResultData` 除了页面正文外，还可能包含 `metadata`、`links`、`imageLinks`、`visitKey`
- 当网页正文过长时，`VisitWebResultData` 还可能包含 `contentSavedTo`、`contentTruncated`、`originalContentLength`

### 3. 系统 / 设备 / 应用结果

常见类型：

- `SleepResultData`
- `SystemSettingData`
- `AppOperationData`
- `AppListData`
- `AppUsageTimeResultData`
- `BluetoothStateData`
- `BluetoothBondedDevicesData`
- `BluetoothScanResultData`
- `BluetoothSessionData`
- `BluetoothTransferData`
- `BluetoothReadData`
- `BluetoothBleServicesData`
- `BluetoothBleNotificationData`
- `NotificationData`
- `LocationData`
- `DeviceInfoResultData`

其中：

- `SystemSettingData` 包含 `namespace`、`setting`、`value`
- `AppOperationData` 包含 `operationType`、`packageName`、`success`、`details`
- `AppUsageTimeResultData` 包含时间窗口、是否包含系统应用以及每个应用的前台使用时长条目
- `BluetoothStateData` 包含设备是否支持蓝牙、是否已开启以及当前状态
- `BluetoothBondedDevicesData` 包含已配对蓝牙设备列表
- `BluetoothScanResultData` 包含扫描到的设备列表、来源和 RSSI
- `BluetoothSessionData` 包含蓝牙会话 ID、地址和模式
- `BluetoothTransferData` 包含写入字节数
- `BluetoothReadData` 包含读取字节数、UTF-8 文本和 base64 字节
- `BluetoothBleServicesData` 包含 BLE service 与 characteristic 列表
- `BluetoothBleNotificationData` 包含已收到的 BLE 通知列表
- `NotificationData` 提供通知列表和抓取时间戳
- `LocationData` 提供经纬度、精度、地址等信息

### 4. UI 与自动化结果

常见类型：

- `SimplifiedUINode`
- `UIPageResultData`
- `UIActionResultData`
- `CombinedOperationResultData`
- `AutomationExecutionResultData`

其中：

- `UIPageResultData` 包含 `packageName`、`activityName`、`uiElements`
- `UIActionResultData` 描述一次点击、输入、滑动等动作
- `AutomationExecutionResultData` 额外包含 `agentId`、`displayId`、`executionSuccess`、`executionMessage`、`finalState`

### 5. Shell / Intent / Terminal / FFmpeg 结果

常见类型：

- `ADBResultData`
- `IntentResultData`
- `TerminalCommandResultData`
- `TerminalSessionCreationResultData`
- `TerminalSessionCloseResultData`
- `TerminalSessionScreenResultData`
- `FFmpegResultData`
- `StringResultData`

`StringResultData` 很简单，只包含：

- `value`
- `toString()`

很多“控制类 API”都会返回它。

### 6. 工作流结果与工作流结构

`results.d.ts` 不只是工作流返回值，还定义了工作流图本身的数据结构。

常见类型：

- `WorkflowResultData`
- `WorkflowListResultData`
- `NodePosition`
- `StaticValue`
- `NodeReference`
- `ParameterValue`
- `TriggerType`
- `TriggerNode`
- `ExecuteNode`
- `ConditionOperator`
- `ConditionNode`
- `LogicOperator`
- `LogicNode`
- `ExtractMode`
- `ExtractNode`
- `WorkflowNode`
- `WorkflowConnectionConditionKeyword`
- `WorkflowConnectionCondition`
- `WorkflowNodeConnection`
- `WorkflowDetailResultData`

其中：

- `WorkflowResultData` / `WorkflowListResultData` 更偏列表视图
- `WorkflowDetailResultData` 包含 `nodes`、`connections`、`enabled`、统计信息与最近执行状态
- `WorkflowNode` 是五类节点的联合类型

### 7. 软件设置与模型配置结果

常见类型：

- `SpeechTtsHttpConfigResultItem`
- `SpeechSttHttpConfigResultItem`
- `SpeechServicesConfigResultData`
- `SpeechServicesUpdateResultData`
- `ModelConfigResultItem`
- `FunctionModelMappingResultItem`
- `ModelConfigsResultData`
- `ModelConfigCreateResultData`
- `ModelConfigUpdateResultData`
- `ModelConfigDeleteResultData`
- `FunctionModelConfigsResultData`
- `FunctionModelConfigResultData`
- `FunctionModelBindingResultData`
- `ModelConfigConnectionTestOutcome`
- `ModelConfigConnectionTestItemResultData`
- `ModelConfigConnectionTestResultData`

这一部分主要给 `Tools.SoftwareSettings` 使用。

`ModelConfigConnectionTestResultData.success` 表示连接测试没有硬失败，例如请求或工具调用没有报错。它不等于多模态能力已经被证明。

`ModelConfigConnectionTestResultData.verified` 表示本次请求的所有测试项都得到验证。单项 `tests[].outcome` 有三种取值：

- `passed`：该项已验证通过
- `unverified`：请求已连通，但返回内容没有证明对应能力
- `failed`：该项请求或执行失败

统计字段中，`passedTests`、`unverifiedTests`、`failedTests` 分别对应这三类单项结果。

### 8. Chat 结果
#### 逐字段数据结构
| 类型 | 字段与运行时语义 |
| --- | --- |
| `ChatServiceStartResultData` | `isConnected: boolean` 表示服务连接结果；`connectionTime: number` 是连接时间戳，Kotlin 默认使用 `System.currentTimeMillis()`。 |
| `ChatCreationResultData` | `chatId: string` 是新建会话 ID；`createdAt: number` 默认是 Unix epoch 毫秒。 |
| `ChatSwitchResultData` | `chatId: string`、`chatTitle: string` 描述切换目标；`chatTitle` 可为空字符串；`switchedAt: number` 默认是 Unix epoch 毫秒。 |
| `ChatTitleUpdateResultData` | `chatId: string`、`title: string` 是目标会话及更新标题；`updatedAt: number` 默认是 Unix epoch 毫秒。 |
| `ChatDeleteResultData` | `chatId: string` 是删除目标；`deletedAt: number` 默认是 Unix epoch 毫秒。 |
| `ChatInfo` | `id/title: string`；`messageCount: number`；`createdAt/updatedAt: string` 直接由会话模型时间值转成字符串；`isCurrent: boolean`；`inputTokens/outputTokens: number`（宿主字段为 `Long`）；`characterCardName/characterCardId/characterGroupId?: string \| null`。characterCardId 由绑定名称解析；未解析或未绑定时可空。 |
| `ChatListResultData` | `totalCount: number` 是匹配总数；`currentChatId: string \| null`；`chats: ChatInfo[]` 仅包含按 limit 截取后的可见项，因此可能少于 `totalCount`。 |
| `ChatFindResultData` | `matchedCount: number` 是匹配数；`chat: ChatInfo \| null` 是选中的匹配会话，无选中项时为 `null`。 |
| `AgentStatusResultData` | `chatId/state: string`；`message?: string \| null` 是可选详情；`isIdle/isProcessing: boolean` 分别表示空闲和处理状态。`state` 是宿主状态键，不是此类型声明的枚举。 |
| `MessageSendResultData` | `chatId/message: string` 是目标会话和已发送文本；`aiResponse?: string \| null`、`receivedAt?: number \| null` 仅在可用时提供；`sentAt: number` 默认是 Unix epoch 毫秒。 |
| `MessageSendStreamEventData` | `type: string` 当前实现发送 `start` 与 `chunk`；`chatId/message: string` 为目标和原始输入；`waifu: boolean` 表示分段聚合模式；`chunk?: string \| null` 仅用于增量块；`chunkIndex?: number \| null` 从 0 递增；`receivedChars?: number \| null` 按宿主字符串长度累计，不是字节数。 |
| `ChatMessageInfo` | `sender/content: string`；`timestamp: number` 原样取自宿主消息；`roleName/provider/modelName?: string` 在 Kotlin DTO 中默认空字符串。返回前会过滤 `sender == "summary"` 的内部摘要消息，并简化 content 中的 XML block。 |
| `ChatMessagesResultData` | `chatId/order: string`；`limit: number` 是实际生效的条数或区间长度；`messages: ChatMessageInfo[]`；`start/end?: number` 仅区间查询填充，表示从 0 开始的 offset 范围，`end` 包含在内。普通查询默认 `order="desc"`、`limit=20` 并将 limit 限制在 1..200；区间查询默认 `order="asc"`，要求 `0 <= start <= end`，请求条数为 `end - start + 1`。 |
| `ChatCallResultData` | ToolPkg API `1.0.1`：`text: string` 是移除已识别协议 metadata 与工具 XML 后的助手文本；`turns: PromptTurn[]` 保留助手文本段及模型工具调用，`kind` 当前由实现产生 `ASSISTANT`/`TOOL_CALL`；`finishReason: "stop" \| "tool_call"`，有工具调用 turn 时为 `tool_call`；`metadata: JsonObject`，有识别到的 meta tag 时含 `protocolMeta: [{provider, payload}]`；`receivedAt: number` 默认 Unix epoch 毫秒；`toString()` 返回非空 `text`，否则返回 finish reason 摘要。 |

这些数据类声明的 `toString(): string` 返回面向日志/展示的文本，不是 JSON 数据字段。时间戳的单位只对上述 Kotlin 默认由 `System.currentTimeMillis()` 生成的字段明确为毫秒；`ChatMessageInfo.timestamp` 直接沿用消息模型值。
#### 包装结果
`ChatServiceStartResult`、`ChatCreationResult`、`ChatListResult`、`ChatFindResult`、`AgentStatusResult`、`ChatSwitchResult`、`ChatTitleUpdateResult`、`ChatDeleteResult`、`MessageSendResult`、`ChatMessagesResult` 都扩展 `BaseResult` 并包含对应的 `data` 字段。失败时优先检查包装的 `success` 与 `error`；不能仅凭 data 中的占位值判断操作成功。
#### 角色卡结果字段
| 类型 | 字段与运行时语义 |
| --- | --- |
| `CharacterCardListResultData` | `totalCount: number` 为列表总数；`cards: CharacterCardInfo[]`；其 `toString()` 输出列表摘要。 |
| `CharacterCardInfo` | `id/name/description: string`；`isDefault: boolean`；`createdAt/updatedAt: number` 对应宿主 `Long` 时间字段。该轻量条目用于 Chat Manager 的角色卡列表。 |
| `CharacterCardToolAccessConfigResultItem` | `enabled: boolean`；`allowedBuiltinTools/allowedPackages/allowedSkills/allowedMcpServers: string[]`。字段分别表示配置开关与允许项名称/ID 列表。 |
| `CharacterCardResultItem` | 完整角色卡配置：`id/name/description/characterSetting/openingStatement/otherContentChat/otherContentVoice/advancedCustomPrompt/marks: string`；`attachedTagIds: string[]`；`chatModelBindingMode: 'FOLLOW_GLOBAL' \| 'FIXED_CONFIG'`、`chatModelConfigId: string \| null`、`chatModelIndex: number`；`memoryProfileBindingMode: 'FOLLOW_GLOBAL' \| 'FIXED_PROFILE'`、`memoryProfileId: string \| null`；`toolAccessConfig: CharacterCardToolAccessConfigResultItem`；`isDefault: boolean`；`createdAt/updatedAt: number`（宿主 `Long`）。结果转换会先将工具访问配置规范化。 |
| `CharacterCardsResultData` | `totalCount: number`；`activeCharacterCardId: string \| null`；`cards: CharacterCardResultItem[]`；`toString()` 只输出数量与当前 ID 摘要。 |
| `CharacterCardResultData` | `card: CharacterCardResultItem` 与 `activeCharacterCardId: string \| null`；单卡完整配置及当前激活项。 |
| `CharacterCardCreateResultData` / `CharacterCardImportResultData` | 创建结果有 `created: boolean`、导入结果有 `imported: boolean`；两者均返回 `card` 和 nullable `activeCharacterCardId`。创建还返回 `changedFields: string[]`。 |
| `CharacterCardUpdateResultData` | `updated: boolean`、更新后的 `card`、nullable `activeCharacterCardId` 与 `changedFields: string[]`。 |
| `CharacterCardDeleteResultData` | `deleted: boolean`、`characterCardId: string`、nullable `activeCharacterCardId`。 |
| `CharacterCardActivationResultData` | `activeCharacterCardId: string \| null`；`null` 表示没有当前激活卡。 |
| `CharacterCardExportResultData` | `characterCardId: string` 与导出的 `tavernJson: string`。 |

角色卡的 API 操作结果仍要结合外层 `ToolResult.success` 判定；失败路径可能带占位 data，不能仅凭 `created`、`updated` 或 `deleted` 字段推断调用整体成功。显式声明 `toString()` 摘要的角色卡结果 DTO 包括列表、单卡、创建/更新/删除/激活/导入/导出结果；嵌套 `CharacterCardInfo`、`CharacterCardResultItem` 与工具访问配置只声明为数据结构，没有额外的自定义 API 方法。
### 9. 记忆查询与链接结果
#### `MemoryQueryResultData`
| 字段 | 声明类型 | 运行时语义 |
| --- | --- | --- |
| `memories` | `MemoryQueryResultMemoryInfo[]` | 本次返回的记忆项。查询会先按 snapshot 去重，再应用结果上限；已在同一 snapshot 返回过的项计入 `excludedBySnapshotCount`。 |
| `snapshotId?` | `string \| null` | 查询 snapshot 标识。未指定时生成 UUID；指定时复用已存在的 snapshot，首次使用时创建。 |
| `snapshotCreated?` | `boolean` | 本次调用是否创建了 snapshot；数据类默认值为 `false`。 |
| `excludedBySnapshotCount?` | `number` | 本次搜索中因 snapshot 去重而排除的匹配数；默认 `0`，不包括超过结果上限而未选择的项。 |
| `toString()` | `string` | 返回格式化文本；空结果会按 snapshot 信息决定是否附加摘要。该方法不是 JSON 字段。 |

#### `MemoryQueryResultMemoryInfo`
| 字段 | 声明类型 | 运行时语义 |
| --- | --- | --- |
| `title` | `string` | 记忆标题。 |
| `content` | `string` | 普通记忆通常返回正文；通配查询可能只返回短摘要。文档记忆按命中的 chunk 形成内容，最多展示 5 段；通配查询或 limit 大于 20 时只给文档/分块摘要。 |
| `source` | `string` | 记忆来源字段。 |
| `tags` | `string[]` | 记忆标签名称列表，不是标签实体对象。 |
| `createdAt` | `string` | 使用设备默认 locale 格式化为 `yyyy-MM-dd HH:mm`。 |
| `chunkInfo?` | `string \| null` | 文档记忆命中 chunk 时的摘要，例如 `Chunk 2/8`；普通记忆、未找到具体 chunk 时为空或省略。 |
| `chunkIndices?` | `number[] \| null` | 命中的 chunk 索引；值按零起始，展示在 `chunkInfo` 中时会加一。普通记忆或无 chunk 命中时为空或省略。 |

#### `MemoryLinkResultData`
| 字段 | 声明类型 | 运行时语义 |
| --- | --- | --- |
| `sourceTitle` | `string` | 关系起点记忆标题。 |
| `targetTitle` | `string` | 关系终点记忆标题。 |
| `linkType` | `string` | 关系类型字符串；允许值由 Memory API/存储实现决定。 |
| `weight` | `number` | 关系强度；声明注释描述范围为 `0.0` 到 `1.0`，Kotlin 数据模型使用 `Float`。 |
| `description` | `string` | 关系描述；字段必填，空文本与否由调用参数/实现决定。 |
| `toString()` | `string` | 返回包含起点、终点、关系类型和强度的摘要文本；不是 JSON 字段。 |

#### `MemoryLinkQueryResultData`
| 字段 | 声明类型 | 运行时语义 |
| --- | --- | --- |
| `totalCount` | `number` | 当前返回的 link 条目数。执行器在过滤掉端点记忆缺失的 link 后，以实际 `links.length` 赋值；它不是不受 limit 限制的全库总数。 |
| `links` | `Array<{ linkId: number; sourceTitle: string; targetTitle: string; linkType: string; weight: number; description: string }>` | 查询返回的 link 快照。`linkId` 在 Kotlin 模型中为 `Long`，`weight` 为 `Float`，序列化后均表现为 JSON number。 |
| `toString()` | `string` | 无 link 时返回 `No memory links found.`；否则逐项显示 ID、端点、类型、权重，并在描述非空时追加描述。不是 JSON 字段。 |

#### 包装结果
- `MemoryLinkResult` 与 `MemoryLinkQueryResult` 都扩展 `BaseResult` 并包含 `data`。包装的 `success`/`error` 语义见本页[结果包装约定](#命名约定)；具体工具发生失败时，仍以工具实际返回的 `ToolResult` 为准。
- `MemoryQueryResultMemoryInfo.chunkInfo` 和 `chunkIndices` 在声明中既可选又可为 `null`；调用端应同时容忍字段缺省和显式空值。
- 查询 snapshot 用于同一查询链/并行查询去重；snapshot 状态按当前实现保存在 profile 关联的进程内 store，不应当作持久化记忆数据。

## 示例

### 使用 `FileContentData`

```ts
const file = await Tools.Files.read('/sdcard/a.txt');
console.log(file.content);
console.log(file.size);
```

### 使用 `VisitWebResultData`

```ts
const page = await Tools.Net.visit('https://example.com');
console.log(page.title);
console.log(page.links?.length ?? 0);
if (page.contentSavedTo) {
  console.log(page.contentSavedTo);
}
```

### 使用 `WorkflowDetailResultData`

```ts
const detail = await Tools.Workflow.get('workflow_123');
console.log(detail.nodes.length);
console.log(detail.connections.length);
```

## 如何阅读这份文件

推荐按“谁返回它”来反查：

- 文件相关 → `files.d.ts`
- 网络相关 → `network.d.ts` / `okhttp.d.ts`
- 系统相关 → `system.d.ts`
- UI 相关 → `ui.d.ts`
- 工作流相关 → `workflow.d.ts`
- 软件设置相关 → `software_settings.d.ts`
- Chat 相关 → `chat.d.ts`
- 记忆相关 → `memory.d.ts`

## 相关文件

- `examples/types/results.d.ts`
- `docs/doc-src/package-dev/files.md`
- `docs/doc-src/package-dev/network.md`
- `docs/doc-src/package-dev/system.md`
- `docs/doc-src/package-dev/ui.md`
- `docs/doc-src/package-dev/workflow.md`
- `docs/doc-src/package-dev/software_settings.md`
- `docs/doc-src/package-dev/chat.md`
- `docs/doc-src/package-dev/memory.md`
