# PR 完整 Android 验证

[返回 CI 计划](../README.md)

## 问题与范围

以回滚分支为目标的七项 PR 被 dev 目标过滤排除，且原有路径分类可跳过 assembleDebug。维护者要求回滚及七项独立功能都实际执行构建和 JVM 测试。

## 实施

- 移除 PR 目标分支过滤，使叠加 PR 自动检查固定合并候选
- 添加 ci:full-android 标签与规划器 --full-android 参数，显式要求完整构建和 JVM 测试
- 聚合状态继续要求完整阶段成功；保留无 secret、不发布、不合并的 PR 工作流
- 清理恢复旧版本带回的一处 FFmpeg 尾随空白

## 验证与交付

- 执行规划器强制作用域与真实 Git 候选 CLI 回归，并执行现有门禁测试
- 在回滚及七项 PR 上启用完整验证，逐项记录固定 head、候选、assembleDebug 和 testDebugUnitTest 的真实结果
- 所有 PR 保持 open，合并等待维护者明确许可
