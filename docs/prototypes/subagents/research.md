# 子代理设计研究记录

本记录来自本地七份项目源码的只读研究，重点关注执行、上下文、消息、取消与交互。没有运行这些参考项目的测试；其中的测试文档属于原作者记录，不作为本轮验证结果。

可确认的本地快照是 OpenMinis `1a71df0`、ZCode `fec97590`、OpenCode `d9eb208`，Claude Code 为 `2.1.88` 的提取源码。Codex、OperitRy 和 Magpie 未取得可核实的有效提交标识，结论只对应本次读取的本地内容。

## 先确定交互基准

OperitRy 与 Operit 同源，是最直接的界面参考。它把协作能力组织成“主聊天紧凑事件卡 → 详情中的状态和结果 → 独立子会话”，另外提供任务管理与兄弟代理切换。主聊天能保留整体方向，详细探索过程可以按需打开。

本次 [HTML 原型](index.html) 采用这条主线，并把手机详情设计成底部面板，宽屏设计成侧栏。任务卡、独立过程、补充指令和结果汇总都可以体验。它是用于讨论的交互草案，尚未决定正式系统的工具契约和运行时架构。

## OperitRy：同源实现与消息语义

主要参考 `docs/agent/subagent-v2-implementation.md`、聊天组件中的 `SubagentAgentCard.kt`、`SubagentManagement.kt`、`SubagentChatPresentation.kt`，以及 `core/agent/collaboration/` 下的 Coordinator、Models、Limits、Mailbox 和 Checkpoint。持久化任务记录位于 `chat-storage` 的 `SubagentRunEntity.kt`。

聊天入口使用 v2 协作操作：`spawn_agent`、`send_message`、`followup_task`、`interrupt_agent`、`list_agents` 和 `wait_agent`。文档前半段曾描述版本切换，后续实现记录已明确普通聊天始终走 v2；旧入口仍用于内部专用场景。

- `send_message` 只写入信箱，不启动空闲代理；`followup_task` 才启动后续执行
- 中断停止当前执行，代理身份与子会话可以继续保留
- 普通父聊天空闲时，报告保留在信箱等待下一轮；Orbit talk 的自动唤醒规则另行处理
- 身份、消息、配置快照与 checkpoint 有持久记录，进程丢失的运行任务标为中断
- 默认全树子代理限制为 3、深度为 1；模型槽不足时明确拒绝，避免父代理占槽等待子代理

卡片先打开详情，详情再进入子会话。任务管理包含搜索、状态过滤、停止与归档。原实现中“前台”指用户委派任务，并不等于必须阻塞主代理；原型改称“本次委派”，与“系统后台”区分。

原型用显式“让主代理汇总”入口保留报告送达和报告消费之间的区别。底部面板样式、两个运行名额以及固定广播规则属于本次演示设计。

## OpenMinis：移动端调度与恢复

主要参考 Android 源码中的 `tools/AgentTools.kt`、`AgentJobRegistry.kt`、`SessionConcurrencyManager`、`ChatViewModel.kt` 和子代理终态记录。

`subagent_task` 聚合委派、状态、补充、取消与恢复。默认后台返回任务 ID，最终结果随后送回主聊天。子代理只有任务所需背景与独立历史，任务说明必须自包含。

每个父聊天最多运行 3 个子任务，全局父会话另有 6 个名额；子任务不占父会话槽，防止父等子形成死锁。子代理默认不能继续委派，也不暴露用户询问和记忆写入。浏览器标签按会话归属，终态负责释放。

中断与完成分开记录，恢复沿用历史，但不承诺浏览器句柄或外部进程仍然存活。移动端最值得借鉴的是排队可见、轮边界补充、按项取消和诚实呈现进程中断。

## ZCode：把代理身份与一次执行分开

主要参考 `apps/zcode-cli/packages/core/src/subagent/`，以及 contracts 中的 `subagent.port.ts` 和 `docs/agent-background-subagent-join.md`。

它明确区分任务、代理、执行与子会话身份。停止时携带预期 execution ID，避免迟到的停止命令误停后一次执行。子任务完成通知通过父命令队列送回，只有真正入队之后才能记录通知成功。

旧版“父轮次一直等待后台 join”的思路已被文档标记为废弃，目标是父轮次可以结束，由完成通知继续工作。队列接收也不等于父模型已经读取。

工具策略在运行时限制角色和目标，例如子代理的 `RespondToCoordinator` 由运行时绑定接收方。电脑控制限制同时落在可信 runtime scope 和工具暴露层，提示词不能承担资源隔离职责。文档里的实验 fork、worktree 和团队路径仍需按版本分别验证。

## Codex：代理树、配置快照与邮箱

主要参考 `codex-rs/core/src/tools/handlers/multi_agents_v2/`、`agent/child_config.rs`、`agent/control/mailbox.rs`、`completion.rs` 和 `spawn_guard.rs`。本地同时保留 v1 与 v2，不应把它们概括成完全相同的接口。

子代理配置从当前实际执行步骤获取模型、provider、工作目录、沙箱与批准策略，避免复制过时配置。代理树与创建过程有所有权保护：创建尚未交接就被取消时，要清理已创建资源和关系。

邮箱区分仅入队与启动下一轮；卸载会话的未读消息也可保留。所读 completion 路径使用 `trigger_turn:false`，因此不能概括为“子代理完成后总会自动唤醒父代理”。值得借鉴的是配置继承、创建取消清理与消息归属。

## OpenCode：后台任务与可靠结果投递

主要参考 `packages/core/src/tool/plugin/subagent.ts`、`session/subagent-job.ts`，以及 execution 中的 `subagent-report.ts` 和 `subagent-completion.ts`。

这份本地新版的 `subagent` 始终后台执行，默认独立上下文，传入已有 session ID 可以继续同一子会话；服务端验证该会话确实属于当前父会话。模型覆盖有明确规则，不能任意替父任务切模型。

任务生命周期超过启动工具的调用栈。服务关闭与用户取消分开处理，结果使用持久 outbox、通知 ID、父 inbox 与确认来交接。父会话已删除、通知 ID 冲突等情形也有明确处理。

最值得参考的是“执行完成”和“结果可靠送达”分成两个阶段。移动端同样需要面对进程消失或主聊天暂时不活跃，单纯在内存回调里拼一条消息不足以覆盖这些场景。

## Claude Code：角色、工具边界与实验 fork

主要参考提取源码 `package/src-extracted/src/tools/AgentTool/` 中的 `AgentTool.tsx`、`runAgent.ts`、`forkSubagent.ts` 和内置角色，以及角色目录加载逻辑。

普通子代理有独立 query loop、可变状态和 sidechain transcript；它仍可处于同一应用进程，不能理解成天然拥有操作系统进程隔离。同步与后台执行的取消所有权也不同。

Explore、Plan 和通用角色具有不同工具面。实验 fork 会继承已经渲染的系统提示、工具和历史以利用缓存，并在调用处限制递归。这与只传任务简报的新上下文是两种设计，首版应先选择明确的一种。

角色工具限制值得参考，但“只读”的提示本身不能证明 shell 或共享设备资源受到沙箱约束。

## Magpie：配置分发和观测

主要参考 `internal/library/subagents.go`、`internal/sessions/subagents.go` 和 `gateway/desktop_subagent.go`。

Magpie 管理角色定义在不同 harness 之间的格式映射与分发，用每文件 hash 记录管理所有权；它也索引父子 transcript，并可通过请求 metadata 路由模型和 effort。

这些能力适合参考角色配置、会话观测与用量归因。它的网关路由不等同通用子代理执行调度器，不能直接拿来解释任务生命周期。

## Operit 已有基础与待讨论点

现有 Operit 已有按聊天隔离的 `EnhancedAIService.getChatInstance(chatId)`，单次执行有 `MessageExecutionContext`、权限会话和 `TurnInputInbox`，服务租约管理配置生命周期。`ChatEntity.parentChatId` 也已经存在，可表达父聊天关系。

UI 自动化专用的 `run_ui_subagent` 和 PhoneAgent 任务登记同样存在。通用子代理可以研究复用这些执行入口，但还需要独立定义任务身份、当前执行身份、父工具调用归属、消息投递、持久终态和管理入口。现有父聊天字段不应被误写成“完全没有父子关系”。

后续方案建议先讨论以下几项：

- 首版采用单层委派、任务简报加独立历史，还是允许 fork 部分主历史
- 主代理什么时候继续，报告到达后如何提示、入队和唤醒
- 任务身份与一次执行身份的区别，以及取消如何约束到目标执行
- 共享终端、浏览器、UI 自动化和文件修改的资源归属
- 进程中断、消息未消费、重复送达以及父聊天删除的持久化规则

目前先通过原型确定信息层级和常用操作，不进行正式后端移植。
