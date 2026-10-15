---
fork: https://github.com/3316891527/Operit
branch: feat/integrated-distribution
status: integrating
---
# 独立发行集成

以 [Release 38675165272](https://github.com/3316891527/Operit/actions/runs/38675165272) 的固定提交 `39a0abaaf23446e5122f7951b5350f4f40b9d52d` 为起点，按用户给定的 52 个 PR 编号顺序整合当前分支代码。来源提交保存在 [pr-sources.json](./pr-sources.json)。

原基点已包含聊天 UI 改版、Waifu Markdown 修复及可与正式版共存的 Kong Release 打包配置。此分支承载独立发行整合，原有功能 PR 分支继续保留各自用途。

## 集成步骤

1. 固定全部 PR 来源提交，调查 #1338 的范围扩大原因
2. 按编号逐项合并，记录已包含项与人工冲突处理
3. 统一数据库迁移、资源与调用关系，保留全部已选功能
4. 提交最终记录并从本分支派发 Release 和 Tests，实际结果以运行链接为准

## #1338 调查

#1338 的源分支 `fix/waifu-markdown-rendering-1280` 后来兼作 APK 集成分支，并合入 #1372 的聊天 UI 改动及 Kong 打包配置，因此 PR 相对 `dev` 扩大到 285 个文件。Waifu 修复本身并不需要如此多的改动。其当前源提交正是本次发行基点，顺序整合时按已包含记录。

## 验证状态

集成进行中。尚未执行本轮 Android 构建、JVM 测试或设备验证；工作流派发与结果将在整合完成后记录。
