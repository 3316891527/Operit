# 代码示例渲染与协议复制

本项属于 PR #1278 的固定 JSON 导入回归，目标分支为 `dev`。

M05 的行内代码和波浪号围栏已经固定输入复现；M10 的聊天正文隐藏协议片段，但用户确认复制预览中的纯文本和 XML 源仍包含协议负载。B01 的格式空白输入框由用户确认已修复。

## 实现

`streamnative/StreamOperators.cpp` 在块级 XML 识别前，用原生行内代码插件保护反引号范围，并以普通正文输出完整分隔符，供下一阶段生成 `INLINE_CODE`。保护结束时不增加段落边界，前后文字仍属于同一段；范围外的真实工具标签继续生成 XML 节点。原生行内插件按连续反引号长度匹配，支持单反引号和双反引号。

`StreamMarkdownFencedCodeBlockPlugin` 记录开始围栏的字符和长度，支持反引号及波浪号。结束围栏必须使用同一字符，长度不短于开始围栏，可带行尾空格、制表符或 CRLF；代码正文中的较短围栏和另一种围栏继续留在代码中。

`ui/common/markdown/FencedCodeBlockContent.kt` 为代码显示与纯文本复制提供同一正文提取入口。Canvas 代码组件和 `MarkdownPlainTextRenderer` 只去除匹配的外层标记，读取语言名，并保留正文的缩进、空白行及嵌套围栏。过去仅识别三反引号，波浪号会留在正文中，四反引号中的三反引号示例也会被误删。

`ChatArea.kt` 的 `buildMessageCopyContent` 从 `displayContent()` 生成三个复制模式的内容，多选复制复用同一入口。XML 复制清理按 `MessageSection.Protocol` 分类过滤，而不是只匹配少量 provider 名称。因此固定输入中的 `provider="openai"` 和其他已分类协议片段均不会进入复制预览和剪贴板，协议负载仍保留在消息原文和 sections 中。代码范围内的 meta 标签示例在 XML 源复制中继续保留。

## 验证

- 独立 C++ 检查直接调用实际原生 Markdown session，覆盖固定 JSON 的 M05 原文、所有两段切分位置和逐字输入、代码段后的真实工具、围栏类型与长度、未闭合围栏、双反引号正文和段落边界。基线为 1/13，修复后为 13/13，退出码分别为 1 和 0。
- `FencedCodeBlockContentTest` 的 6 个纯 JVM 用例已执行通过，覆盖两类围栏、较短及不同围栏、较长结束围栏、CRLF/缩进、流式未闭合和空代码块。
- `MessageCopyTextTest` 新增 3 个用例，覆盖四种 provider 的隐藏、原始协议负载保留、多选复制及字面 meta 示例；该组等待 Android Tests 工作流运行结果。
- `NativeMarkdownCodeProtectionTest` 新增 4 个 Android 仪器用例，覆盖实际 native 分包和最终 Markdown 节点；设备仪器用例尚未执行。独立 C++ 检查通过不等同于设备画面复测。

Android Build 和 Android Tests 使用 PR 的 fork 分支派发，构建与应用级 JVM 结果以工作流为准。

## 真机回归状态
用户确认 M01–M12 全部通过，包括 M05 代码示例展示与 M10 协议复制。R01–R09 由用户反馈通过，截图中的 R10–R13 静态显示符合预期；M03 空结果不提供详情展开和复制属于原有规则，思考内部不显示专用工具调用卡片属于预期设计。

R14 六组正文未入图；会话重开、导出往返与 L01–L06 实时执行没有独立新增结果。用户决定跳过实时检查，本轮不消耗模型额度；完整操作、已通过项、未测项及可填写表见[设备回归](./04-device-regression.md)。

[已完成源码修复与本轮静态 UI 回归记录；工作流和未测项单独登记]
