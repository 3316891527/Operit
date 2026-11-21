---
owner: https://github.com/3316891527/Operit
branch: feat/markdown-mermaid-preview
status: markdown-extensions-device-validation-pending
---

# Markdown 代码块与图表预览

聊天正文和工作区 Markdown 预览共用渲染器。没有语言标签的代码块，其换行和复制按钮被工具栏布局放到了左侧；分隔线上下只有 2dp 留白；Mermaid 预览需要点击并从 CDN 下载脚本。

本轮将工具按钮固定在右侧，分隔线上下留白增加到 12dp，并让完整的 Mermaid 代码块默认显示离线图表。保留源码、复制、缩放和全屏操作。未闭合的流式围栏继续显示代码，闭合后才绘图。

## 范围与步骤

1. [共享布局与离线 Mermaid](01-implementation.md)
2. [检查与设备验收](02-validation.md)
3. [SVG 显示范围与缩放修正（历史方案）](03-svg-viewport.md)
4. [原生 Canvas 实现](04-native-canvas.md)
5. [Markdown 扩展与编辑器体验](05-markdown-extensions-editor.md)
6. [扩展检查与验收](06-extension-validation.md)
7. [枚举遗漏的编译修复](07-enum-compilation-fix.md)
8. [设备测试导入与 CI 覆盖修正](08-instrumentation-compilation-fix.md)

当前 Mermaid 使用 Kotlin 解析与布局、Android Canvas 绘制，已移除两次未通过设备验收的 WebView/SVG 方案。修改围绕 `EnhancedCodeBlock`、代码围栏节点、分隔线节点和原生图表组件展开。保持 Markdown 文本和文件格式兼容，不改变其他格式的导入导出。常见扩展语法另列支持范围与待办，不把新增语法隐含为已经完成。

## 交付

交付见 [PR #230](https://github.com/3316891527/Operit/pull/230)。开发基线为 `dev` 的 `1e346500fe32e0a81e48aa4412d20d7cfc22c083`，通过 GitHub API 核实远程分支指向相同提交。
