---
fork: https://github.com/3316891527/Operit
status: ready-for-ci
---

# 内置浏览器浮窗内容递归修复

## 原状与目标

打开内置浏览器浮窗时，日志显示 `StackOverflowError`，`WebSessionBrowserHost.kt:264` 与 `ComposeView.Content()` 反复交替，后续省略的栈帧超过四万条。日志没有记录页面 URL 或 APK 提交版本，当前证据指向浮窗容器创建路径。

`ensureCreated` 在 `ComposeView(appContext).apply` 中使用 `setContent { Content() }`。未限定接收者的 `Content()` 被 Kotlin 解析为内层 `ComposeView.Content()`，再次执行同一内容 lambda，导致无限递归。

目标是在创建浮窗时正常调用浏览器宿主的页面内容。工作台入口使用显式的 `host.Content(...)`，最小化指示器直接调用明确命名的组件，均没有同样的裸调用。

## 修正范围

在浮窗内容 lambda 中使用 `this@WebSessionBrowserHost.Content()`，并添加中文原因注释。仅修改浏览器宿主与本任务文档，从 `dev` 建立独立修复分支。

## 本地验证与交付

使用 Kotlin 2.0.21 并面向 JVM 17，从修改前后的生产代码提取浮窗 `ComposeView(...).apply` 表达式与宿主 `Content` 默认参数签名，分别编译并执行最小复现。修改前触发 `StackOverflowError`，宿主内容调用为 0 次；修改后正常完成，`ComposeView.Content` 与宿主内容各调用 1 次。编译字节码也分别指向 `ComposeView.Content()` 与宿主的 `Content$default`。

浏览器宿主、浮窗入口和工作台浏览器控制器共 3 个相关生产文件通过 Kotlin 编译器 PSI 语法检查。

最小复现中的 Android 与 Compose 类型使用桩，仅验证 Kotlin 接收者解析。此次未在本地运行完整 Android 编译或设备 UI 测试；真实页面显示、最小化与恢复仍需设备验收。

提交后执行仓库卫生、Markdown 链接与本地化检查，并在独立 PR 为新提交派发 Debug Build 和 Android Tests。Android Tests 工作流运行 JVM 与脚本回归，不包含设备 UI 测试。确认运行建立后停止查询进度，完整 CI 结果尚未确认。

[DONE]
