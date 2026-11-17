---
title: 编辑器语法配色
status: pending_validation
---

# 编辑器语法配色

[返回计划](index.md)

浅色语法配色采用 Atom One Light 风格，复用 TextMate 分词与主题刷新路径。编辑区背景、默认正文、光标、选区、搜索和行号继续由应用主题提供。

## 浅色语法色板

颜色参考 Atom One Light 的[基础色板](https://github.com/atom/one-light-syntax/blob/master/styles/colors.less)与[语法样式](https://github.com/atom/one-light-syntax/blob/master/styles/syntax/_base.less)。注释使用同套色板中较深的 mono-2，改善手机浅色背景下的辨识度。

| 分类 | 色值 | 对应色板 |
| --- | --- | --- |
| 关键字 | #A626A4 | 紫色 hue-3 |
| 类型 | #C18401 | 橙黄色 hue-6-2 |
| 字符串 | #50A14F | 绿色 hue-4 |
| 注释 | #696C77 | 深灰 mono-2 |
| 数值 | #986801 | 深橙色 hue-6 |
| 变量与属性 | #E45649 | 红色 hue-5 |
| 函数 | #4078F2 | 蓝色 hue-2 |

画布编辑器与 Diff 通过共享渲染器使用 codeColor 绘制函数；浅色主题中的 codeColor 与 selectorColor 均为 #4078F2。处理标记、标题、引用和列表色也使用 One Light 色板。后续已接入完整动态样式，保留 Markdown 字体效果，并开放[轻量 JSON 自定义规则](../../../doc-src/dev-core/editor-highlighting/editor-highlighting.md)。

## 实现与验证

配色集中在 EditorTheme.kt 的 LightTheme 中，深色主题沿用现有设置。本轮调整颜色值，不增加回归测试源码；静态核对源码语法、差异格式与文档链接，本地不执行编译、构建或测试。

远程 Build、Android Tests 和 PR Check 的运行记录由同一 PR 维护。实际构建结果、设备主题切换和视觉效果分别验收，静态核对不能替代设备验证。

[DONE]
