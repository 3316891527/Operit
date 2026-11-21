---
fork: https://github.com/3316891527/Operit
branch: fix/chat-processing-status-flicker
---

# 总结后继续调用工具时的界面跳动

用户提供的录屏截图显示：历史摘要之后继续批量调用工具时，输入栏上方有时出现几十行重复的执行工具名，随后变回单行接收状态。工具明细也会从收起状态切换到占满视口。截图能确认布局尺寸变化，不能独立证明所有闪烁都由同一原因引起。

源码及原视频中可确认三个问题：

- 工具组自动展开依赖 XML 子流是否存在，一个块完成就收起，下个块开始又展开，快速连续调用使整组反复改变高度
- 智能体和经典输入栏的处理状态文字没有行数限制，`ExecutingTool` 又直接拼接批次中所有工具名。多次同名调用会重复占行，测量出的输入栏高度随之改变，并进入聊天列表的底部留白
- `AIChatScreen` 收集流式通知后执行滚动动画，`ChatArea` 同时按正文和节点变化立即滚动到末尾。两条协程竞争同一个列表的滚动状态

处理方式：

- 两种输入风格共用单行状态文本，超长内容显示省略号，原文保留在文字语义中；后台工具调用、结果及计数保持原有数据。
- 生成执行状态文案时按同名工具聚合剩余数量，例如 `create_file * 5`。每个工具返回结果时按真实完成顺序递减，只剩一次时显示工具名，归零后移除该项；全部完成转入处理结果状态。排队与执行中的调用均计入剩余数量，权限拒绝和执行失败也视为已完成，取消冻结事件不递减。进度文案追加在计数后方。
- 处理结果状态同样按工具名聚合，模型历史和实际结果保持原有格式。
- 流式通知转交 `ChatArea`，与消息、节点、底部留白变化共用同一个自动跟随入口；用户上翻关闭跟随、窗口加载期间不抢占位置。

设备回归检查同一状态组件在短提示、57 个同名工具、多个不同工具和带换行进度之间切换时高度不变。该用例写入设备测试，需在设备上运行；常规 Android Tests 工作流只运行 JVM 与脚本回归。

原视频补充确认：全片约 13.42 秒，按时间顺序抽帧，并连续检查开头约 1.5 秒原始帧，可见工具明细在批次之间反复展开、收起。主要问题来自工具组的自动展开依赖 XML 子流：一个 XML 块完成时 `completeXmlBlock` 会移除子流，`describeGroup` 随即失去展开条件；下一块开始又恢复，连续调用时整组反复改变高度。

补充修复将自动展开依据改为整段消息是否仍在流式生成。同一连续工具组在批次间隙保持展开，出现后续正文或整段消息结束后再收起，手动选择仍优先。补充六项 JVM 回归覆盖子流间隙、连续追加、正文边界、流结束和手动选择；两项设备回归走真实流式解析与时间线渲染，在 XML 子流清空后检查工具明细仍然存在，同时覆盖整块与拆分行渲染。

剩余数量增加五项 JVM 回归，覆盖五次同名调用逐次递减、多种工具乱序完成、排队与取消事件、重复事件、批次编号偏移和包工具名。状态栏高度用例也加入计数变化及处理结果的切换。

人工验收需要在总结后继续流式输出和批量工具调用，观察状态栏、工具分组自动收合、历史摘要插入、手动上翻和恢复底部跟随。低频闪烁仍需新 Debug 包验收。

状态：工具组开合、剩余计数、状态栏和滚动修复的代码及回归用例已完成 [DONE]。新增 JVM 回归由 Android Tests 工作流执行，设备测试及真机视觉验收待进行；实际检查和 CI 发起记录位于 PR #226。

设备回归命令：

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.ai.assistance.operit.ui.features.conversation.chat.modules.input.common.ChatProcessingStatusTextAndroidTest

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.ThinkToolsAutoExpansionAndroidTest
```