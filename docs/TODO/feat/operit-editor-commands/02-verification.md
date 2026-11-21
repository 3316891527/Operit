# 验证与交付

本次实现从 `origin/dev` 的 `1e346500fe32e0a81e48aa4412d20d7cfc22c083` 开始，使用独立分支 `feat/operit-editor-commands`。旧 Markdown PR 不包含本次改动。

## 已完成验证

| 检查 | 结果与范围 |
|---|---|
| TypeScript 5.9.3 类型检查与生成 | 全部示例类型检查通过，定向生成使用 ES2020 / CommonJS、严格空值与返回检查选项 |
| 生成资源一致性 | 通过，重新生成的脚本、示例 JavaScript 与内置 assets 逐字一致 |
| 命令核心 JUnit | 27 项通过，实际编译命令解析、执行、调度、目录声明和文件处理的生产实现 |
| 脚本与生产 SDK 桥接 Node 回归 | 9 项通过，包括数组与 JSON 编码、空参数、错误传播、旧软件升级提示、六层嵌套与十二个并发调用 |
| Kotlin 语法检查 | 全部改动的 Kotlin 调用方、新增实现和回归文件通过 |
| 既有接口核对 | 既有宿主注册与 JS SDK 方法保留；新增执行器、SDK `exec` 和调试安装入口 |
| 差异空白 | `git diff --check` 通过 |

核心 JUnit 包含 `PlatformCommandRegistryTest`、`PlatformCommandExecutorTest`、`PlatformCommandDispatcherTest` 与 `PlatformPackageFilesTest`。本地通过 Kotlin 编译器直接编译这些生产文件及对应结果、目录数据声明，使用真实 `org.json`、序列化与 JUnit 依赖执行，没有重新写一份解析器来代替生产代码。

本机没有完整 Android SDK。上述核心回归和 Kotlin 语法检查不等于整个应用已编译。包安装的文件打包、替换与路径边界使用真实文件系统验证；Android 包管理器、配置服务和权限 UI 的端到端执行仍需完整构建及新版软件验收。

脚本回归加载实际生成的 `operit_editor.js`，并提取实际 `JsTools.kt` 中的 `SoftwareSettings.exec` 桥接，目标业务调用使用受控替身。`OperitEditorRuntimeTest` 把此 Node 回归接入现有 JVM 测试入口，因此 Android Tests 和 PR 检查都会运行它。

首轮核心回归发现角色卡与性能接口已有注册、缺少帮助参数声明，已补齐并重跑 27 项全部通过。调试安装的 ToolPkg 启用与重新激活通过目标工具权限检查，旧公开调试安装方法保留原默认行为。

## 提交与 CI

提交前检查差异、仓库规范、本地 Markdown 链接和本地化。独立 PR 目标为 `dev`，针对最终提交发起 Build 与 Android Tests，并核对运行记录的 `head_sha`。按照协作约定，只确认启动与提交对应关系，不轮询最终工作流进度；具体链接与 SHA 记录在 PR 和交付回复中。

本次没有安装或替换设备上的软件，也没有修改用户的模型、角色卡、包开关和语音配置。`cleanOnExit` 排查结果未混入本次实现。

## 发行要求

编辑器源码由 4515 行缩减为 67 行，生成 JavaScript 为 63 行。元数据只公开一个 `operit_editor(args)` 工具，旧的 `query` 与多工具脚本包装已移除。

新版必须随 1.12.2+27 软件上线，并在发行说明中明确同步升级软件的要求。旧客户端不能通过新加元数据获得事后的最低软件版本门禁；缺少宿主接口时明确报错，不调用旧手册或其他执行路径。

[DONE]