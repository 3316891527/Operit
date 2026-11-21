# 原生 Mermaid 检查

生产实现位于 `app/src/main/java/com/ai/assistance/operit/ui/common/markdown/mermaid/`。Kotlin 负责解析、分层布局及绘制指令，Android Canvas 负责文字、节点、箭头、图例和时间轴。Mermaid 预览不创建 WebView，不加载 HTML、SVG、JavaScript 或网络资源。

## 核心测试

`NativeMermaidTest` 覆盖解析、子图分组、方向、循环、中文和 Unicode 标签、文字换行、关系语义、常用图表布局、完整适配、缩放锚点和复位。测试使用固定文字宽度测量器，检查真实生产布局与变换，不包含 Android UI 桩。

先通过仓库 Gradle wrapper 下载 Gradle 8.13，然后将其 `lib` 目录作为唯一参数：

```sh
./gradlew --version
bash ci/mermaid-native/run-core.sh /path/to/gradle-8.13/lib
```

脚本使用该分发自带的 Kotlin 编译器与 JUnit，输出只存在于临时目录。Android Tests 的独立 `Mermaid native core` 作业执行此检查，应用 JVM 测试也会发现同一个测试类。

## 设备绘图

`MermaidRendererAndroidTest` 使用生产 Canvas 视图和 Android Paint，覆盖 8 类图表在 3 种尺寸下的完整范围、缩放复位及 Bitmap 的非背景像素，并检查错误源码修改后的原生预览。它属于 instrumentation 测试，普通 JVM 作业不会执行。

真实字体与手势、Compose 中的预览和全屏切换仍需 Android 验收。核心测试通过不能代替设备绘图，也不能证明所有 Mermaid 语法兼容。

## 当前语法范围

| 类型 | 支持范围 |
| --- | --- |
| `flowchart` / `graph` | TD/TB/BT/LR/RL，常用框形，中文/引号/换行标签，箭头、虚线、粗线、边标签，链式和多目标连接，嵌套子图及方向，节点颜色类 |
| `stateDiagram` / `stateDiagram-v2` | 状态、别名、起点/终点、转移标签、组合状态 |
| `classDiagram` | 类、成员、继承、组合、聚合、关联、依赖及关系标签 |
| `erDiagram` | 实体、属性和基本基数关系 |
| `mindmap` | 缩进层级、根节点及常用框形 |
| `sequenceDiagram` | 参与者与别名、消息与返回、自调用、注释、编号、激活、循环和分支框 |
| `pie` | 标题、数值、切片、百分比及图例 |
| `gantt` | 标题、分区、YYYY-MM-DD 日期、整天时长、已有任务依赖、完成/活动/关键状态及里程碑 |

这是明确的原生语法子集，不是完整 Mermaid 引擎。`click`、`linkStyle`、任意 CSS、网络图片、图标、配置 frontmatter、其他图表语言以及未实现的变体会明确报错；不忽略无法识别的关系语句。标签中的格式标记只作为文字处理。

输入最多 200000 字符；图最多 400 个节点和子图、2000 条边，子图最多嵌套 24 层；时序图最多 100 个参与者、2000 个事件。超出限制显示错误，不截断内容。