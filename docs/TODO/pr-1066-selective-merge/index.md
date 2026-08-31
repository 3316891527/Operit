---
fork: https://github.com/AAswordman/Operit
source_pr: https://github.com/AAswordman/Operit/pull/1066
base_branch: dev
working_branch: refactor/pr-1066-selective-merge
---

# PR 1066 选择性合并

## 原本状况

PR 1066 以 `dev` 为目标，包含 8 个连续提交，改动横跨翻译缓存、Chat Runtime State、工具执行、Provider、思考配置、聊天渲染、主题设置、示例、文档和测试。

当前工作分支从 `dev` 创建，用于承载可独立审阅的局部改动，并让 PR 1066 最终以人工合并提交结束。

## 选择范围

- 保留 `8a4f756f`：工具和终端会话处理修复，以及该提交配套的类型、示例、文档和测试改动
- 保留 `cd2017b1` 中 `OpenAIProvider.kt` 的协议元数据分隔修复
- 不带入翻译缓存、Chat Runtime State、整套聊天渲染/主题方案和可编辑思考配置
- 不带入只服务于未选方案的后续测试与配置修正

## 操作步骤

1. 从当前 `dev` 创建本分支
2. 应用选定提交和单文件修复
3. 检查最终树、差异和提交图
4. 推送本分支并将 PR 1066 的目标改为本分支
5. 创建保留本分支树、同时以 PR head 为第二父提交的人工合并提交
6. 推送合并提交，确认 PR 1066 状态为 merged

## 验证范围

- 执行 `git diff --check`
- 核对分支、PR base/head、合并提交父提交和远端状态
- 本次不执行构建或测试命令，原因是项目执行准则要求除非明确指定不得默认运行
