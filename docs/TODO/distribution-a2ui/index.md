---
repo: https://github.com/3316891527/Operit
branch: feat/distribution-a2ui
source: https://github.com/CATMIAOZHI/Operit/tree/9902726bec086d2950e6472c14f6acb9cca85413
status: implementation-complete-awaiting-cloud-verification
---
# 原生 A2UI 与包体优化

本轮以包含 11 项设置及原生翻译调整的个人分支提交 `6803712c` 为基础。目标是在本分支的 MessageSection 和长消息分片路径接入原生卡片，并减少完整 Material 图标库带来的安装包开销。

源码接入已完成；编译、单元测试和 APK 尺寸以云端结果为准。未在本地构建或测试，也未宣称真机验证通过。

已完成静态核对：Python / YAML / XML 和工具元数据解析、动态界面资源引用、105 项官方源码 SHA-256、Material 图标源码引用覆盖及暂存差异检查。Kotlin 结构扫描中参考源码已有的解析器提示不作为编译结论；类型和运行时兼容性由云端验证。共准备 123 个 JVM 用例和 24 项完整手动回归条目。

- [依赖、来源与包体输入](01-dependencies-and-size.md)
- [消息、交互与状态接入](02-chat-integration.md)
- [完整回归清单及结果表](03-regression.md)
- [动态界面工具说明](../../agent/dynamic-ui.md)

开发验证只使用 Operit Debug。正式展示版本在用户验收并明确要求发布时改为 `1.12.2+2`，内部 versionCode 继续递增，Kong 包名和签名保持一致。当前工作不发布 Release，不改变正式更新源，也不轮询 CI。
