---
fork: https://github.com/3316891527/Operit
branch: fix/chat-processing-status-flicker
---

# 总结后继续调用工具时的界面跳动

用户提供的录屏截图显示：历史摘要之后继续批量调用工具时，输入栏上方有时出现几十行重复的执行工具名，随后变回单行接收状态。工具明细也会从收起状态切换到占满视口。截图能确认布局尺寸变化，不能独立证明所有闪烁都由同一原因引起。

源码中可确认两个问题：

- 智能体和经典输入栏的处理状态文字没有行数限制，`ExecutingTool` 又直接拼接批次中所有工具名。多次同名调用会重复占行，测量出的输入栏高度随之改变，并进入聊天列表的底部留白。
- `AIChatScreen` 收集流式通知后执行滚动动画，`ChatArea` 同时按正文和节点变化立即滚动到末尾。两条协程竞争同一个列表的滚动状态，历史窗口重载、节点变化和状态尺寸变化时更明显。

处理方式：

- 两种输入风格共用单行状态文本，超长内容显示省略号，原文保留在文字语义中；后台工具调用、结果及计数保持原有数据。
- 生成执行状态文案时去重工具名称。
- 流式通知转交 `ChatArea`，与消息、节点、底部留白变化共用同一个自动跟随入口；用户上翻关闭跟随、窗口加载期间不抢占位置。

设备回归检查同一状态组件在短提示、57 个同名工具、多个不同工具和带换行进度之间切换时高度不变。该用例写入设备测试，需在设备上运行；常规 Android Tests 工作流只运行 JVM 与脚本回归。

人工验收需要在总结后继续流式输出和批量工具调用，观察状态栏、工具分组自动收合、历史摘要插入、手动上翻和恢复底部跟随。当前修复只处理已确认的布局与滚动问题，低频闪烁仍需新 Debug 包验收。

状态：代码实现与设备回归用例已完成 [DONE]。设备测试及真机视觉验收待进行，常规 CI 的发起和结果单独记录在 PR 中。

设备回归命令：

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.ai.assistance.operit.ui.features.conversation.chat.modules.input.common.ChatProcessingStatusTextAndroidTest
```