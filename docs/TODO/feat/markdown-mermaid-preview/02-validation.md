# 检查与设备验收

## 当前实现

Mermaid 目前使用 Kotlin 解析和布局，Android Canvas 绘制。初版 WebView 和后续 SVG 修订均未通过用户设备验收，相关网页、JavaScript、资源例外与浏览器回归已删除。先前 JavaScript 检查不能作为当前原生实现的验证结果。

## 已完成的检查

原生核心 `NativeMermaidTest` 已运行，23 项测试通过。测试直接使用生产解析、布局和视口代码，检查中文、节点形状、带标签连线、链式与多目标连接、子图嵌套、方向、环和自环、文字换行、8 类常用图表、完整边界、缩放锚点及复位。

Android Canvas 绘图器和视图已使用 Android API 独立编译；这不包含 Compose 入口和完整 APK，不能代替完整应用构建。共享入口与 instrumentation 文件通过 Kotlin PSI 语法检查，该检查不解析依赖类型。

Android Tests 工作流已用 YAML 解析器检查重复键及作业步骤结构。独立 `Mermaid native core` 作业执行同一核心测试，不需要 Chromium、Android SDK 或模拟器。完整应用 JVM 作业也会发现该测试类。

独立设备绘图验证没有完成，不能计入通过项。应用已有的 instrumentation 保留真实 Bitmap 像素断言，尚待在正常应用测试环境执行。当前没有成功生成的设备绘图截图；详见 [原生 Canvas 实现](04-native-canvas.md)。

## 尚未执行的 instrumentation

`EnhancedCodeBlockAndroidTest` 两项继续覆盖空标签和长标签的右侧按钮对齐，以及流式围栏闭合时的预览切换。

`MermaidRendererAndroidTest` 两项已替换为原生测试：8 类图表在 3 种尺寸下检查完整边界、缩放复位与非背景像素，另检查非法源码报错后修改为合法源码能显示原生预览。普通 JVM CI 不执行这些 instrumentation 方法，不能计入已通过数。

## 用户设备反馈

用户已明确反馈原生 Canvas 的八类图表均正常运行，效果符合本机使用需要。此反馈记录为八类图表的人工设备验收，不代替尚未运行的 instrumentation，也不推及本轮新增 Markdown 语法。

## 应用内验收

- 聊天与工作区分别检查空语言、长标签、复制和换行按钮位置，以及分隔线 12dp 留白
- 检查原生 Mermaid 的中文标签、分支、子图、全屏、单指拖动、双指缩放、双击与按钮复位、旋转
- 流式围栏闭合后开始预览，切换源码和复制时保留完整文本
- 非法或未实现语法明确显示错误及行号，修改源码后重新绘制
- 长对话滚动、图表离屏返回、全屏关闭、页面退出后的视图回收及内存

CI 在推送后按协作约定发起并核对最终 SHA，链接记录在 PR；成功发起后不轮询工作流进度。

## 支持边界

当前是明确的原生 Mermaid 子集，范围见项目的 `ci/mermaid-native/README.md`。不包含完整 Mermaid 引擎、任意 CSS、配置指令、网络图片或其他未列出的图表语言。源码兼容不代表所有扩展语法都可原生绘制。