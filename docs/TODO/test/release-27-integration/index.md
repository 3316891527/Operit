---
owner: https://github.com/3316891527/Operit
branch: test/release-27-integration
status: in-progress
---

# 1.12.2+27 更新统一集成与验收

用户要求将 #222～#235 汇总到独立分支，使用同一个 Debug 包统一验收，避免子代理等交叉功能重复安装测试。基线为 dev 的 1e346500fe32e0a81e48aa4412d20d7cfc22c083，#105 不在本次范围。

#224 完整包含 #222 的提交历史，直接合入 #224；#234 与 #235 不是祖先关系，原型文档和实际实现分别保留。使用保留父提交的合并，便于核对来源、追踪冲突和后续回到 dev。

1. [来源与合并记录](01-merge-sources.md)
2. [统一验证与交付](02-validation.md)

状态：进行中。
