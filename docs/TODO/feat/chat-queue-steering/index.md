---
fork: https://github.com/3316891527/Operit
branch: feat/chat-queue-steering
status: completed
upstream_pr: https://github.com/AAswordman/Operit/pull/1481
upstream_commit: 446b76c1f4a4633b3bf8d0b469879fde1519cff5
---

# 生成中消息排队与引导

生成回复时可将补充消息排队等待，或明确交给当前轮处理。队列项保留自己的附件与引用；引导在采样或工具完成的安全边界接入，取消和失败保留未消费输入。

[实施与验收](01-implementation.md) · [返回分类索引](../README.md)
