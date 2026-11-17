# Operit2 变更审阅移植来源

本目录的 `WorkspaceGitParser.kt` 根据 [Operit2](https://github.com/AAswordman/Operit2) 的 `plugins/packages/buildin/change_review/src/git.ts` 移植，固定来源提交为 `1a05a948cf66e56b2e0fb7b496bfe4e939cafb44`。

同级 `changes/WorkspaceDiff.kt` 的行内公共前后缀裁剪和相邻增删行配对参照该提交中的 `change_review/src/diff.ts`。完整快照差异使用本项目已有的 java-diff-utils；Compose 页面、快照存储、文件执行器采集和 Android／SSH 传输为原生适配。

原代码来源作者为 Operit2 项目贡献者。所移植目录没有单独的许可证文件，按来源仓库根目录的 GNU AGPL version 3 保留许可。这些衍生文件标记 `AGPL-3.0-only`，许可证原文见 [LICENSE-AGPL-3.0.txt](LICENSE-AGPL-3.0.txt)。来源仓库 Rust workspace 中的 MIT 字段不作为这些 TypeScript 文件的许可依据。

移植保留 NUL 分隔文件名和重命名配对，增加绑定目录筛选、跨边界重命名单侧显示、明确的比较分支、成功操作快照，以及按发起对话隔离的异步文件调用。原插件注册和聊天气泡汇总没有移入。


原生审阅页的紧凑工具栏、可调整文件区、文件筛选、状态栏、单双栏边界与折叠条布局，参照同一固定提交的 `change_review/src/ui/workspace.ui.ts` 重新以 Android Compose 实现，对应 `WorkspaceChangesScreen.kt`、`WorkspaceChangesControls.kt` 和 `WorkspaceDiffView.kt`，保留 `AGPL-3.0-only` 标记。本轮再次对照维护者提供的 Operit2 checkout 中的同一固定提交，统一紧凑工具栏、并排文件区和来源菜单。桌面 `code -g` 适配为内置文本编辑器，reveal 适配为已有文件管理窗口，terminal 适配为检查后端归属后的终端会话；Git 查询始终只读。新增原生项目树、快照统计和有界缓存不引入桌面程序依赖。

只读代码溯源为本项目原生实现，使用 Git `--line-porcelain` 协议和提交对象父列表，不移植桌面 Git 写操作。编辑快照映射复用既有 `WorkspaceDiff`，历史窗口复用上述原生差异查看器。
