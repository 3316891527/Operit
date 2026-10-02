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
- 整体任务仍未完成：覆盖索引列出 18 个需逐项审计的声明组和 1 项待核对字段；`results.d.ts` 的 162 个接口、742 个顶层字段中，本轮已逐字段核对 30 个接口/134 个字段，其余 132 个接口/608 个字段尚未完成同等核对。其他模块 API、ToolPkg 注册字段、Compose renderer 和示例也未完成；旧文档保持原样，未提交或推送。
