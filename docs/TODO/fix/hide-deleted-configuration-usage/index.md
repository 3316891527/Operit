---
fork: https://github.com/3316891527/Operit
branch: fix/hide-deleted-configuration-usage
status: implemented_pending_ci
---

# 清理统计列表中的已删除配置

统计页配置详情会显示历史配置对应的使用量。当前名称查询会为已经删除的配置构造带 ID 的临时名称，造成 UUID 名称残留。


在配置数量左侧增加“清除无效配置”，按真实配置列表判断配置是否仍存在。清理仅持久化隐藏配置 ID，原始用量、费用定价、模型累计、周期概览和趋势查询保持原样。


- [实现与验收](01-hide-deleted-configurations.md)
- [返回修复索引](../README.md)
