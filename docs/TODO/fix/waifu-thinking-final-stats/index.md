---
repository: https://github.com/3316891527/Operit
branch: fix/waifu-thinking-final-stats
base: feat/reopen-pr-176-theme-foundation
status: implemented_pending_ci
---

# Waifu 思考过程与分句统计

Waifu 消息展示链当前会过滤 think/thinking，且同一请求的统计被复制到每个分句。本次仅调整 Waifu 显示：思考标题使用“思考过程”，展开内容沿用既有结构；同一回复的分句只在最后一条显示时间和 token 数据。

1. [思考块保留与分句统计](01-thinking-and-final-statistics.md)

思考展示部分已按用户要求由[Waifu 正文展示计划](../waifu-reply-only/index.md)移除；仅末条显示请求统计的行为继续保留。
