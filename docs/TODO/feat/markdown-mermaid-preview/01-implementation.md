# 共享布局与原生 Mermaid

## 布局调整

代码块语言标签始终占据工具栏剩余宽度，无语言标签时也保留右侧按钮的位置，超长标签单行省略。共享分隔线的上下留白从 2dp 改为 12dp。

## 当前原生实现

Mermaid 围栏闭合且非空后默认显示图表，开放的流式围栏继续显示源码。源码切换、复制和全屏继续沿用原有入口。

Kotlin 解析器生成明确的节点、关系和图表模型；纯 Kotlin 布局根据文字测量安排节点、嵌套子图、连线及时间轴。Android Canvas 直接绘制图形和文字。整体范围由全部绘制指令计算，预览、缩放、平移和复位共用一个变换。

Mermaid 专用的 WebView、HTML 页面、JavaScript 包及 Playwright 检查已移除。此实现覆盖常用 Mermaid 语法子集，未实现的语句会带行号报错；具体范围见项目根目录的 `ci/mermaid-native/README.md`。

## 常见语法对照

| 语法 | 本轮状态 |
| --- | --- |
| 表格、删除线、围栏代码 | 继续使用现有路径 |
| 行内与块级数学公式 | 继续使用现有 LaTeX 路径 |
| Mermaid 围栏 | 本轮完成离线、默认预览与闭合时机修正 |
| 脚注、定义列表 | 后续需要增加块级定义与引用解析 |
| GitHub Alerts | 后续需要识别引用块类型并提供专用样式 |
| `==高亮==`、上下标 | 后续需要内联插件，避免误解释普通文本 |
| 任务列表 | 后续单独核查复选框语义与渲染，不能把普通列表显示视为完整支持 |

参考 [GitHub 图表文档](https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/creating-diagrams)、[Mermaid 使用说明](https://mermaid.js.org/config/usage.html)和 [Markdown Guide 扩展语法](https://www.markdownguide.org/extended-syntax/)。扩展语法不存在统一的完整超集，应按明确的输入与行为逐项实现。

[DONE] 共享布局、原生绘制与围栏闭合时机已经实现；应用内集成验收记录见检查文档。
