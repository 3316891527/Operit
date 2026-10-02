---
title: ToolPkg 开发者文档重建
status: in_progress
personal_repository: https://github.com/3316891527/Operit
upstream_target: dev
related_discussion: https://github.com/AAswordman/Operit/discussions/1328
---

# ToolPkg 开发者文档重建

## 当前情况

- Discussion #1328 指出插件教程对 JavaScript 基础讲解过多，没有系统说明宿主提供的 API。
- 当前文档分散在 `docs/doc-src/package-dev/`、`docs/TOOLPKG_FORMAT_GUIDE.md`、`docs/SCRIPT_DEV_GUIDE.md` 和 `docs/SCRIPT_DEV_SKILL.md`。
- `examples/types/*.d.ts` 描述静态类型；运行时实现位于 `app/src/main/java/.../core/tools/packTool/`、`core/tools/javascript/` 和 `plugins/toolpkg/`。
- ToolPkg API 兼容性实现目前支持 `1.0.0`；`1.0.1` 从 Operit `1.12.1+4` 起支持。包版本、宿主 API 版本和归档格式版本是不同字段。
- 旧文档仍被 README、安装脚本、应用提示和测试引用，本阶段不删除或改写旧文档。

## 目标

在 `docs/doc-src/toolpkg_developer_documentation/` 建立新的单一完整参考入口。文档以 JS/TS 开发者为目标，不重复教授通用语言基础；以声明文件、运行时实现和验证过的行为为依据，覆盖 ToolPkg 格式、运行模型、全部公开 API、版本门槛、Hook 契约、类型、示例和调试发布流程。

每个公开方法都应说明完整签名、参数约束、异步行为、返回结构、错误语义、状态/副作用、调用前置条件和可运行示例。Hook 还应写明触发时机、执行顺序、payload 可变性、返回值对宿主的影响、超时/取消/异常处理和版本要求。不能从类型声明推断未证实的运行时行为。

## 范围

- 纳入 ToolPkg manifest、子包脚本、全局运行时、工具模块、Android/Java Bridge、UI/Compose DSL、Hook、公共结果类型、内置库和 WASM 接口。
- 对每个版本化字段或方法标注 ToolPkg API `@since` 要求，并区分 Operit 最低支持版本、包自身版本和格式版本。
- 为公开符号建立声明文件与文档页面的覆盖清单，并记录声明与运行时不一致或尚无证据的项目。
- 旧文档在本阶段保留。新文档完整后再单独审阅兼容入口、引用迁移和删除范围。
- 不扩写通用 JavaScript/TypeScript 教程，不虚构 API，不修改运行时实现。

## 阶段

1. 完成 API、类型、版本和 Hook 的源码盘点。
2. 建立分类目录、总索引、版本/兼容说明和开发者快速路径。
3. 撰写格式、运行时、模块 API、Hook、UI、结果类型与库参考。
4. 逐符号核对声明/实现/文档，检查链接、示例和旧文档引用；只记录待删除候选，不删除旧文档。
5. 完成后再与维护者评估旧文档如何迁移或删除。

## 分支与 PR

- 分支：`docs/toolpkg-developer-reference`
- PR 目标：`dev`
- 个人仓库：`https://github.com/3316891527/Operit`
- 当前只进行本分支文档工作；是否提交、推送和创建 PR 在新文档达到可审阅状态后处理。

## 本轮进度

- 已按 `examples/types/*.d.ts` 补齐 QuickJS Runtime、Java Bridge、Pako、Compose DSL、Material 3、Material Icons 的新文档入口，并为 Compose/库分类建立索引。
- 已根据源码更正 `dataUtils`、Pako、`useState`/`useMemo`、QuickJS console/timer 等运行时契约；差异记录在[覆盖索引](../../doc-src/toolpkg_developer_documentation/09_compatibility/coverage.md)。
- 新文档树的相对 Markdown 文件链接检查已通过；本轮复核覆盖 45 篇 Markdown，断链为 0。NativeInterface 声明与 `JsEngine` 原生绑定均为 33 项，核心页逐项覆盖 33 项。
- `software_settings.d.ts` 的 26 个公开方法均已列出 TypeScript 签名；环境变量、沙箱/MCP、speech 更新、角色卡字段校验与工具访问解析、连接测试行为已有源码核对。本轮完成模型配置创建/更新/删除、字段归一、函数绑定/索引和连接探针行为审计，并记录 `ModelConfigUpdateOptions` 缺少 6 个运行时支持的 `llama_*` 字段这一声明差异。
- `workflow.d.ts` 的 10 个 `Runtime` 方法均已列出签名；本轮完成 CRUD、patch、节点/连接解析、启停/错误结果及 `WorkflowExecutor` 的触发选择、依赖调度、边条件、失败分支、Condition/Logic/Extract/Execute 节点行为源码审计。
- `quickjs-runtime.d.ts` 页面现在区分 QuickJS 全局兼容层与 metadata 驱动的包工具参数转换；后者按 `JsToolManager` 实现记录类型转换、缺参和错误路径。
- 整体任务仍未完成：覆盖索引列出 18 个需逐项审计的声明组和 1 项待核对字段。`results.d.ts` 的 162 个接口名现均在参考页中出现，38 个 `BaseResult` 包装到 `data` 类型的映射已逐项核对；此前补入 22 个结果数据接口字段表，并记录 `DateResultData`、蓝牙字面量类型、模型配置 DTO、工作流节点及文件/网络/UI/终端结果差异。本轮完成系统/设备/应用结果 22 个接口/86 个字段，以及软件设置/沙箱/MCP/语音服务结果 13 个接口/104 个字段的运行时核对；并发现 `SpeechServicesUpdateResultData.sttApiKeySet` 当前取自 TTS API key。结果类型累计核对 136 个接口/671 个声明字段，另有 26 个接口/71 个字段尚未完成同等核对。`toolpkg.d.ts` 的注册、Hook、资源/持久配置、IPC 和 WASM API 已按源码核对；其他模块 API、Compose renderer 和示例仍需继续核对。旧文档保持原样。此前草稿已推送到个人仓库分支 `docs/toolpkg-developer-reference`；本轮修改已提交，尚未推送。
