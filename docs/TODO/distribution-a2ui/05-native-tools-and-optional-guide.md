# 原生工具与外部可选引导

仓库：https://github.com/3316891527/Operit

分支：`feat/distribution-a2ui`

原状：Express 编译和 Compose 渲染已经是 Kotlin，但模型生成、修订和实时数据更新仍通过内置 `dynamic_ui.js` 调用 JS NativeInterface。移除脚本会同时移除这些工具入口。

目标：将生成、修订、数据更新、组件目录和图标查询注册为原生工具；外部引导包仅返回说明和示例，不执行生成操作，也不随 APK 内置。将宿主和测试包由 `ui.common.dynamicui` 改为 `ui.common.a2ui`，测试资源目录一并改名，保留历史状态存储目录。

作用域：新增独立 `core/tools/defaultTool/a2ui/`，复用可信 ToolRuntimeContext、CardDelivery 与 CardLiveStore；接入现有工具权限和角色卡可见性。旧 JS 生成桥接在本次尚未正式发布的 A2UI 迭代中移除，已有完整卡片仍通过原生渲染器读取。

验证：新增原生入口的活动请求、输入校验、引用隔离与修订用例；静态检查后派发 Debug 和 Android Tests，不执行本地构建或测试。正式发布暂停，等待 Debug 验收。

实现：

- 新增 A2uiToolRuntime、A2uiTools 和 A2uiToolDefinitions，直接注册五个原生工具；移除 JsEngine 中的 A2UI 生成桥接与专用上下文捕获。
- 41 个宿主 Kotlin 文件、23 个测试文件和 29 个测试资源移动到 a2ui 路径，同步全部 Kotlin 引用和资源查找路径。既有 dynamic_ui 状态存储目录保留。
- 从 assets 和打包白名单移除 dynamic_ui.js。外部 a2ui_guide.js 只包含 read_guide/read_example，不调用 NativeInterface 或生成函数；原始完整脚本已经单独备份到手机。
- 原生入口增加 7 个 JVM 用例，覆盖无需引导包生成、可信上下文、请求结束、无效输入、引用隔离、修订和 live 版本控制。完整人工清单保留原有条目并扩充至 40 项。

[DONE] 源码实现已完成；静态核对、云端检查和 Debug 验收分别记录，不将源码完成视为已通过运行验证。
本轮静态结果：79 个 Kotlin 文件的结构核对无新增解析器错误；可选引导 JS 语法检查通过；105 项官方来源/适配哈希匹配。未执行本地编译或测试，云端构建和真机结果待确认。
