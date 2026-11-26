---
repository: https://github.com/3316891527/Operit
branch: feat/theme-status-bar
base: 441c77cebde1b129672381b3ec59076aa12d6333
status: implementation-complete-awaiting-ci-and-device-review
---
# 主题工作室系统状态栏

现有选择部件包含消息状态条与应用顶栏，缺少手机系统时间、信号和电量所在的顶部状态栏。系统栏保持透明，图标明暗目前只随应用浅深主题变化。

本次在现有主题脚本的 surfaces 中增加独立 `statusBar`，归入顶栏与导航。复用填色、渐变、图层和透明度，前景仅开放 Android 支持的浅色或深色图标；不增加导航栏配置，不改变消息状态条。

真实应用在 OperitTheme 根画布的页面内容之后绘制状态栏背景，范围仅为系统状态栏 Insets。工作室预览复用绘制接口，时间与图标使用固定样例，预览不操作真实 Window。没有该目标的现有主题继续使用透明状态栏与原来的图标明暗规则。

作用域为脚本契约和类型声明、Studio 能力与标签、运行时及预览、相称接口文档。不开启本机编译、构建或 JVM／设备测试；允许 UTF-8、Kotlin 解析、资源及差异静态核对。PR 与远端验证交主代理收尾。

细化与验收见 [状态栏接入](01-system-status-bar.md)。
