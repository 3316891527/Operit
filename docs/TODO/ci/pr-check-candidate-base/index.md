---
fork: https://github.com/3316891527/Operit
branch: ci/pr-check-candidate-base
base: dev
---

# PR Check 候选基准校验

## 问题与证据

PR #172 的运行 `42506195310` 检出了固定的 `github.sha`，该候选的第一父提交为 `29f6728`，事件中的 `pull_request.base.sha` 仍为 `50069cb`。两者沿同一条目标分支历史前进，原校验却要求逐字相等，导致计划阶段失败，后续检查全部跳过。

工作流的三个检出步骤已经固定同一个 `github.sha`。本次修正候选校验器对事件 base 的解释，继续以固定候选的第一父提交计算路径范围。

## 作用域与实现

- 从候选的两个父提交确定实际 base/head，head 必须与 PR 事件完全一致
- 事件 base 必须是实际 base 的祖先；不相关历史和领先于候选的事件 base 仍会失败
- 路径规划、检查输出和下游工作区保持使用同一份候选快照，目标分支之后前进不会改变本次运行
- Step summary 同时记录事件 base 和实际 base，便于解释事件与候选的时间差

实现入口为 [pr_check.py](../../../../ci/script/pr_check.py)，回归位于 [test_pr_check.py](../../../../ci/test/test_pr_check.py)。[CI 使用说明](../../../../ci/README.md) 同步说明快照契约。贡献分支通过 PR 合入 `dev`。

## 验证目标

回归构造真实 Git 历史：贡献分支只修改翻译，目标分支先增加 native 文件、后增加 Gradle 文件。用旧事件 base 校验固定候选时，应通过且只选择翻译资源检查；后续目标分支改动不应混入路径列表。领先于候选的 base、不相关的 base、错误 head 和非两父提交候选继续被拒绝。

GitHub PR Check 执行门禁回归、仓库检查、文档链接和 Code Runner 检查。部署后重新检查原 Claude PR，确认它能完成候选规划与 JVM 检查，再继续原合并任务。
