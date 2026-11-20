# 设备测试导入与 CI 覆盖修正

## 问题与检查范围

PR 检查在 compileDebugAndroidTestKotlin 阶段报告 MermaidRendererAndroidTest.kt 无法解析 assertExists 的导入。该 API 是 SemanticsNodeInteraction 的成员方法，不是 androidx.compose.ui.test 包中的顶层扩展函数。删除错误导入，保留 onNodeWithText(...).assertExists() 断言。

JVM 单测通过与设备测试源码编译失败可以同时出现，两个源码集的检查范围如下。

| 检查入口 | 源码与行为 |
| --- | --- |
| :app:testDebugUnitTest | 编译并运行 app/src/test 中的本地 JVM 测试 |
| :app:compileDebugAndroidTestKotlin、:app:compileDebugAndroidTestJavaWithJavac | 编译 app/src/androidTest 中的设备测试源码，不启动设备运行断言 |
| :app:connectedDebugAndroidTest | 在连接的设备或模拟器上运行设备测试 |

原独立 Android Tests 工作流只执行第一项。PR 检查由变更计划选择设备测试编译，修改 app/src/androidTest 时会同时选择第二项。因此独立工作流通过不能证明设备测试源码可编译。

## 修正与验证

独立 Android Tests 在 JVM 单测步骤后增加设备测试 Kotlin 和 Java 编译步骤，继续使用现有完整 Android 依赖。PR 检查保留按变更选择任务的行为，步骤名称明确包含设备测试源码编译。

本次只修正导入与 CI 检查范围，不新增测试用例。已核对 AndroidX 的成员方法声明和项目现有 Compose 测试依赖；完整设备测试源码编译由修复提交的 CI 确认，未声称设备测试已执行。

状态：源码与工作流修正完成，CI 编译结果及设备运行待确认。
