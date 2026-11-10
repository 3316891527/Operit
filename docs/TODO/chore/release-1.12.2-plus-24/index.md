---
repo: https://github.com/3316891527/Operit
branch: fix/plugin-update-feedback
status: prepared
---

# 1.12.2+24 发行准备

[PR #191](https://github.com/3316891527/Operit/pull/191) 已将 #178–#190 的统一测试分支合入 dev，来源和合并记录见 [13 项集成清单](../integrate-13-prs/index.md)。本轮继续补充 [插件更新反馈](../../fix/extension-update-feedback/index.md)，递增版本为 `1.12.2+24`、versionCode 80，并保留 main 独立历史完成发布同步。

[发行说明](../../../../ci/distribution/updates/releases/1.12.2+24.md) 使用既有发行仓库、固定签名和 `v1.12.2+23` 增量基线。仓库迁移及合并分支清理保持关闭。

## 验证与交付

- 用户简单体验确认 #183、#184、#185、#186、#187、#189 正常；其余来源的既有静态、定向回归与设备验证范围分别保留，不扩大为全量验收。
- 插件反馈为小范围界面修正，用户明确无需为此单独设备复测；两个修改的 Kotlin 文件已通过 PSI 语法检查。
- 合并前执行仓库整洁、本地化、Markdown 链接和版本递增检查。同步到 main 后由现有 push 触发 Build 与 Android Tests，各运行一次，不重复手动派发同版本工作流。
- 主分支同步、工作流启动、构建测试结果和 Release 公开状态分别记录；发行准备与源码同步本身不代表公开 Release 已成功。

[DONE] 准备版本号、发行说明及最终插件更新反馈；最终同步和 CI 记录保存在对应 PR 与仓库外审阅记录中。

main 历史同步说明：终端自动化的两处文档冲突来自早期 main 部署计划与 dev 已完成部署记录。保留较新的真实工作流运行记录，以及候选基准、完整 Android 验证的新增索引；终端工作流与应用源码没有冲突。
