# 共享布局与离线 Mermaid

## 原实现与意图

`EnhancedCodeBlock` 的工具栏使用 `Arrangement.SpaceBetween`。无语言标签时只剩按钮行，布局把唯一子项放到起点。改用始终存在且占满剩余宽度的标签区域，标签为空也保留空间；超长标签单行省略，给按钮留出位置。

`CanvasMarkdownNodeRenderer` 的分隔线把上下留白从 2dp 改为 12dp，让聊天和工作区共用同一间距。

Mermaid 原实现将源码插入 HTML，使用远程 CDN 和宽松的渲染配置。替换为应用本地的 Mermaid 11.17.2 完整浏览器包、本地预览页面和页面控制器。原包及其内置许可证注释保持原样，记录 npm 包 SHA-512 和文件 SHA-256。

## 预期行为

- Mermaid 围栏闭合且包含正文时默认显示图表。生成中的开放围栏仍显示代码，闭合后可以直接绘图，不需要等待整条回复结束。
- 查看源码、复制源码、预览切换、缩放和全屏继续可用。
- 使用本地 HTTPS assets 地址，只有页面、控制器和 Mermaid 脚本可以加载。禁用外部网络、文件访问、内容访问和页面导航。
- 源码经 JSON 参数传给页面，由 Mermaid `render` API 处理。渲染错误明确展示，用户可手动切到源码检查。
- WebView 随 Compose 平台视图释放而销毁，源码变化更新已有页面，不插入 Android JavascriptInterface。

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


[DONE] 共享布局、离线页面和闭合时机的实现已完成；设备回归尚待执行。
