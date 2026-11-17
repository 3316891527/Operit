---
title: 全文快照空字符串修正
status: pending_validation
---

# 全文快照空字符串修正

[返回计划](index.md)

维护者反馈上一轮提交 `6cbef5e0` 的构建通过，但 Android Tests 的 JVM 阶段失败。既有[失败运行](https://github.com/3316891527/Operit/actions/runs/43746614355)完成 3552 项测试，7 项失败、15 项跳过。

读取该运行保存的 HTML 报告确认：`EditorLineIndexTest` 的 5 项比较断言都取得了空字符串；`EditorIncrementalLayoutTest` 的一项比较也取得空字符串，另一项在读取缓存字符时越界。

## 根因与修正

`EditorTextSnapshot.toString()` 使用 `buildString(length) { lines.appendRange(this, 0, length) }`。闭包内隐式接收者是刚创建的 `StringBuilder`，其中 `length` 为零。代码因此没有追加源文本，并将空字符串缓存为该快照的全文。快照公开长度仍非零，之后索引缓存字符串时又会越界。

将源快照长度在闭包外捕获为 `snapshotLength`，构建缓冲区和追加区间都显式使用同一个源长度。完整字符串仍按版本惰性生成，重复读取共享缓存，不改变行树、软换行或编辑语义。

保存、搜索、预览等需要全文的出口都通过快照读取内容，因此也受这处修正覆盖。既有失败断言完整保留，并在缓存测试中补充转为全文后的逐字符一致性检查。

## 验证边界

原有 7 项失败测试继续作为远程回归检查。本地执行源码、资源、UTF-8 和文档静态校验，没有执行编译、构建或测试。修正后的实际通过结论以新提交的远程 Android Tests 报告为准；运行链接由 [PR #216](https://github.com/3316891527/Operit/pull/216) 维护。

[DONE]
