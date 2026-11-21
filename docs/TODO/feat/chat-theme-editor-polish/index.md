---
owner: https://github.com/3316891527/Operit
branch: feat/chat-theme-editor-polish
status: implemented
---

# 聊天语音、主题背景与编辑器滚动集成验收

将本轮四项体验改动统一提交，以 dev 为目标创建一个 PR，并针对同一提交启动 Debug Build 和 Android Tests。保留原独立工作树，统一验收使用此集成分支。

## 变更范围

- [搜索框展开时保持浏览位置](../../fix/editor-search-scroll-jitter/index.md)，来源提交 72c57f01
- [语音气泡尺寸对齐文字](../../fix/chat-voice-bubble-size/index.md)，来源提交 c0998bda
- [经典与智能体统一长按录音](../chat-voice-hold-recording/index.md)，含常亮、松手发送、两侧取消与转文字以及中断取消
- [全局和页面背景独立模糊](../theme-background-blur/index.md)，各模式单独保存，预览共用正式渲染

长按录音和背景模糊均从对应未提交工作树生成完整快照后合入；两项功能新增的十二条文案在九种语言中一同保留。图层原有模糊、两处不同作用范围的渐变以及其余业务行为保持原有语义。

## 验证记录

集成后的二十三份 Kotlin 文件语法解析、九种语言各十二条新增文案的 XML 与占位符、UTF-8 编码和差异空白检查均通过。生产手势状态机独立编译及十五项 JUnit 回归通过。提交后执行仓库规范、本地化及全量 Markdown 本地链接检查，并在 PR 中记录结果。完整 Android 编译、JVM 单测和打包交给仓库既有工作流；只确认启动及提交一致，不轮询结果。编辑器设备回归、真实录音、系统手势、常亮和背景画质仍需设备验收。

[DONE] 四项改动已集成，本地检查完成；远程构建、测试结果及设备验收仍待确认。

## 设备验收反馈修订

用户已在设备上确认编辑器乱滚动修复和背景模糊可用。本轮补充[语音配置拒绝和主窗口侧滑排除](../chat-voice-hold-recording/02-device-feedback.md)，沿用 PR #232 分支继续验收；修订版语音行为仍待设备复测。
