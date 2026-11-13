---
fork: https://github.com/3316891527/Operit
branch: fix/waifu-reply-only
---

# Waifu 恢复为仅展示正文

用户要求移除先前增加的 Waifu think/thinking 展示。删除独立思考更新、进度标记和持久化发布通道；保留正文预览、分句、打字延迟、重试撤回、朗读及末条请求统计。

1. [DONE] 删除 WaifuThinkingPresentation、Thinking 更新和专属测试，恢复只有正文的流式存储结构。
2. [DONE] Waifu 时间线及消息样式统一隐藏思考，旧记录不改写，普通模式继续沿用原主题设置。
3. [DONE] 补充思考过滤、取消和正文朗读回归，更新展示边界说明，完成静态检查并提交面向 dev 的 PR。

实现记录：删除独立思考事件及进度标记；新增 3 项正文流式回归和 2 项历史思考记录下的末条统计回归。差异与残留引用检查通过。静态语法解析没有新增诊断；MessageProcessingDelegate 原有正则对应的两处解析器诊断与基准一致。编译和运行结果由远端工作流验证。


## JVM 回归测试依赖修正

远端测试在 `WaifuReplyOnlyStreamTest` 的正文分句阶段触发 `UnsatisfiedLinkError`：新增测试调用了完整分句器，而 JVM 环境没有 `streamnative` 原生库。

改用现有 `splitSnapshot` 注入入口，保留生产代码的 `buildRenderableContentForWaifu` 思考过滤、流式队列和消息存储；测试文本的句号分句由测试内的固定规则提供。保留原有三项测试与全部断言，不以缺库跳过测试，不修改产品分句或显示行为。

修复后重新发起 PR Check、Android Tests 和 assembleDebug，运行结果以该修复提交的工作流为准。
