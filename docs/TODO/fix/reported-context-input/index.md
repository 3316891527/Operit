---
repository: https://github.com/3316891527/Operit
branch: fix/reported-context-input
upstream_pr: https://github.com/AAswordman/Operit/pull/1519
---

# 区分上下文窗口估算与服务端上报输入

我们的「上下文用量」明细已经有估算和最近请求两项，但后者从允许发送本地估算的 `onTokensUpdated` 通道取值。窗口估算与服务端 usage 的口径不同；不应用一次历史请求替换发送前的估算，也不能拿它除以当前模型的窗口上限。

上游 #1519 改读 `onUsageReported` 的方向正确；审阅的 `26383d1c` 忽略 attempt，并在确认请求成功前显示部分上报。它也把 ToolPkg 自行提供、未保证来自远端响应的计数展示为服务端上报。因此不直接移植，保留我们现有的明细面板。上游给出的三组数是会话观察，不是固定误差或同一请求的受控对照。

- [采样结束和会话归属](01-reported-input-lifecycle.md)

不改窗口估算、预警配色、自动总结、计费统计、存储格式和其他在审分支。不为没有可信上报的请求编造输入值。
