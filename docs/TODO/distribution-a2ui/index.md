---
repo: https://github.com/3316891527/Operit
branch: feat/distribution-a2ui
source: https://github.com/CATMIAOZHI/Operit/tree/9902726bec086d2950e6472c14f6acb9cca85413
status: implementation-complete-awaiting-cloud-verification
---

# 原生 A2UI 与包体优化

本轮以包含 11 项设置及原生翻译调整的个人分支提交 `6803712c` 为基础。在本分支的 MessageSection 和长消息分片路径接入原生卡片，并减少完整 Material 图标库带来的安装包开销。

模型可直接输出完整有效的 `a2ui` 围栏，也可通过五个原生工具编译 Express、修订卡片和更新 live 数据。外部可选 `a2ui_guide.js` 仅返回说明与示例，旧内置生成脚本及对应 JS 生成桥接已经移除。

宿主、工具与测试统一使用 a2ui 包路径，类型和文件名使用 A2ui 前缀，字段、资源键与私有状态目录使用 a2ui 命名。普通卡片状态和 live 快照保存在应用私有 `files/a2ui/<聊天指纹>/`。功能尚未正式发布，直接使用新目录，不迁移旧调试数据。

配套保留 127 个 JVM 用例和 40 项完整手动回归条目。用户确认命名整理前的调试版本已编译通过；本轮命名及状态目录变更重新派发云端 Debug 和 Android Tests，实机结果等待用户验收。未执行本地构建或测试。

- [依赖、来源与包体输入](01-dependencies-and-size.md)
- [消息、交互与状态接入](02-chat-integration.md)
- [完整回归清单及结果表](03-regression.md)
- [调试反馈修正](04-debug-feedback.md)
- [原生工具与外部可选引导](05-native-tools-and-optional-guide.md)
- [命名与私有状态目录统一](06-a2ui-naming-and-storage.md)
- [A2UI 工具说明](../../agent/a2ui.md)

开发验证只使用 Operit Debug。正式发行等待用户实机验收通过，展示版本按既定计划设为 `1.12.2+2`，内部 versionCode 继续递增，Kong 包名和签名保持一致。当前工作不发布 Release，不改变正式更新源，也不轮询 CI。
