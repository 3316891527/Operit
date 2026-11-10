---
fork: https://github.com/3316891527/Operit
branch: feat/collapse-completed-response-process
status: completed
upstream_pr: https://github.com/AAswordman/Operit/pull/1483
upstream_commit: 6af18584f4213b4c53b3dd4c9b443deab2f8c0bd
---

# 折叠已完成的回复过程

将上游 #1483 的完成回复过程折叠适配到当前消息与 Markdown 架构。保留最终答复、用户消息、历史内容和模型上下文，适配气泡、Cursor 与浮窗，并提供全局显示开关。核对并移除对未引入子代理系统和消息引导层的依赖。

[实施与验收](01-implementation.md) · [返回分类索引](../README.md)
