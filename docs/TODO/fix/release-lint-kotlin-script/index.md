---
fork: https://github.com/3316891527/Operit
branch: fix/release-lint-kotlin-script
status: pending-release-validation
---

# 正式构建的 Kotlin Gradle 脚本 Lint 崩溃

## 问题与证据

1.12.2+26 的[正式发布第二次运行](https://github.com/3316891527/Operit/actions/runs/44087593117/attempts/2)在 `:app:lintVitalAnalyzeRelease` 失败。完整堆栈从 `UastGradleVisitor.visitBuildScript` 进入 `KaFirScriptSymbol`，最终报告 `findFirCompiledSymbol only works on compiled declarations`。这次有明确的 Lint 内部异常，与第一次运行器失联分开记录。

应用通过 `apply(from = "workspace-textmate.gradle.kts")` 引入 TextMate AAR 处理任务，匹配 Android Lint 分析外部 Kotlin Gradle 脚本的[已知缺陷 #463283604](https://issuetracker.google.com/issues/463283604)。Debug 构建没有运行 Release 的 vital lint，因此其通过结果不能替代正式构建检查。

## 修复范围

将 TextMate 构建逻辑原样并入 `app/build.gradle.kts`，合并所需导入并移除外部脚本和 apply 入口。任务名、非传递运行时依赖、上游 AAR SHA-256、四个替换类、生成路径及 builtBy 依赖关联均保持原值，应用运行代码和语法资源不变。

保留 AGP、Kotlin 及 Lint 版本，继续执行 Release vital lint 和既有签名、包名、资源与增量包校验。

## 验证与交付

- 对照迁移前脚本，确认任务声明和执行体逐字节一致，且不再存在 apply 外部脚本入口
- 执行 Kotlin 构建脚本静态解析、差异、仓库卫生和增量 Markdown 链接检查
- 修复先通过 PR 合入 dev，再同步 main；26 尚未公开，版本保持 1.12.2+26、versionCode 82，增量基线仍为 v1.12.2+25
- 从新 main 提交创建新的正式发布工作流，使用 :app:verifyCiPreviewRelease 和 publish_release=true

本地静态检查与真实 Release 构建分别记录。确认新工作流运行记录建立后，按维护者约定停止查询进度；Lint、签名构建及正式公开是否成功，以该次 CI 的实际结论为准。

实现迁移 [DONE]。