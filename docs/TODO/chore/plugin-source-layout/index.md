---
title: Plugin source layout
status: complete
personal_repository: https://github.com/3316891527/Operit
upstream_target: dev
---
# 插件开发材料归组
## 背景
插件示例目前位于仓库根目录 `examples/`，同步工具位于 `tools/example_packages/`，完整 ToolPkg 开发文档位于 `docs/doc-src/toolpkg_developer_documentation/`。这三处属于插件开发与发布域，分散在不同根目录；CI、内置资源指纹和文档链接均依赖现有路径。
## 目标
将插件示例、示例同步工具和 ToolPkg 开发参考归入 `plugins/`，修复仓库内有效路径引用，同时保持插件运行时包、离线资源身份及已发布资源快照不变。
## 范围
- `examples/` -> `plugins/examples/`
- `tools/example_packages/` -> `plugins/tooling/example_packages/`
- `docs/doc-src/toolpkg_developer_documentation/` -> `plugins/docs/toolpkg_developer_documentation/`
- 更新 Gradle 资源输入、资源准备与源码指纹逻辑、同步脚本、CI 路径过滤、活动文档和链接
- 保留 `offline-pack-sources.json` 中指向其固定历史 `sourceRevision` 的路径；资源源码摘要使用稳定的逻辑路径，以保证现有资源指纹不因物理搬迁变化
## 不在本批范围
- Gradle 子项目归组、`web-chat`、CMake 文件迁移、`app/build.gradle.kts` 拆分
- `app/src/main/assets/packages/` 生成文件结构及 ToolPkg 外部标识调整
- 改写已完成 TODO 的历史结论或已发布资源快照
## 步骤
1. [x] 移动三个插件开发目录，并将同步工具对仓库根目录和示例目录的定位改为新路径
2. [x] 更新内置资源生成、源码摘要映射、应用资源输入及 CI 路径过滤
3. [x] 更新活动文档入口、交叉引用和示例路径；保留固定发行快照的历史路径
4. [x] 静态检查仓库内旧物理路径引用、相对 Markdown 链接及差异空白
## 验收
- 插件示例、同步工具和 ToolPkg 开发参考均位于 `plugins/`
- 已发布离线包、包 ID 与资源发行快照保持原样；源码摘要沿用稳定的逻辑路径，纯路径移动不改变指纹输入名，后续实际源码或同步工具修改仍会参与指纹计算
- Gradle、脚本、CI 和活动文档不再依赖旧物理目录
- 本地构建或测试遵循仓库 `AGENTS.md`，未获明确要求不运行

## 静态验收记录
- 606 个原跟踪文件均位于迁移后的路径，旧物理目录中没有剩余跟踪文件。
- 全仓 Markdown 对比检查复用 `ci/script/check_markdown_links.py` 的解析和重命名规则，改用 `git cat-file --batch` 批量读取：新增本地断链 0，基线已有断链 8；修复了迁移暴露的 6 条相对链接。
- Python AST 检查 8 个文件、严格 JSON 检查 1 个正文修改文件通过；原样移动的 JSONC 配置保持原样。
- `app/config/offline-pack-sources.json`、`offline-packs.json` 和 `builtin-extensions.json` 与基线一致。
- 已补充新旧 Git revision 布局生成相同逻辑快照的回归源码，并将当前资源准备夹具迁到新物理布局。
- 本地未运行编译、构建、测试或设备验证；回归执行结果以 PR 的 CI 为准。
