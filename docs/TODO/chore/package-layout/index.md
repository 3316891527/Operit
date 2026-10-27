---
repo: https://github.com/3316891527/Operit
branch: refactor/package-layout
base: 6a4b9bfb399c670321518089592ae3e02e38897e
status: implemented-awaiting-ci
---
# API 包职责与命名整理

本轮按维护者批准的范围，完成原 API 117 个 Kotlin 源文件的归属整理：74 个文件移动路径，43 个文件保留位置，并更新同文件附加声明、调用方和相关测试的引用。没有修改业务执行流程。

- [原始职责分析](analysis.md)
- [本轮实施记录](01-api-migration.md)
- [117 个源文件的最终路径与类名](api-migration-candidates.csv)
- [机器可读迁移映射](api-migration.json)
- [静态核对与完整验证清单](verification.md)

## 已完成范围

1. [DONE] 固定全部 117 个原始文件、顶层声明、原始摘要和目标路径。
2. [DONE] 统一 speech/stt、speech/tts 及 audio、wake、runtime，完成五处类名消歧义。
3. [DONE] 将模型访问公共支持、对话编排、记忆提取与 ToolPkg 适配归入对应职责包。
4. [DONE] 更新同包隐式引用、显式 import、完整名称、文档链接和 37 个测试文件的路径。
5. [DONE] 比较改动源码正文和字面量，核对范围外源码摘要、对外入口与退役名称。
6. [PENDING] 工作分支仓库构建与 JVM 测试结果；提交后派发，结果由维护者反馈。

## 本轮边界

保留 api 顶层名、根包前缀、namespace、applicationId、配置值、Android 组件与已确认的 Java 桥入口。EnhancedAIService 和 AIForegroundService 保留原完整类名；提供方实现类名保持现状。未添加旧包转发类。

util、UI/data、三个 MCP 路径不一致文件、Widget、DocumentsProvider 及根包品牌整理留在后续独立任务。本轮没有执行本地应用构建或测试，未合入 dev/main，也未发布版本。
