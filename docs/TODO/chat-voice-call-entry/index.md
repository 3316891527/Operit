---
fork: https://github.com/3316891527/Operit
branch: feat/chat-voice-call-entry
status: implemented-awaiting-ci
---
# 语音通话入口调整

Agent 与 Classic 输入框在空闲且没有正文和附件时显示灰色禁用发送按钮。正文或附件存在时正常发送，生成期间保留取消及草稿排队操作。

语音通话移动到两种附件面板中，使用电话图标和现有“语音通话”文案。沿用原麦克风权限申请与全屏通话启动方式，不改动通话实现。Agent 的两种布局都同步调整。

验证范围：两种输入模式、空白正文、仅附件、正常发送、取消与排队、通话授权及拒绝。语音条不在本轮范围内。构建与 JVM 验证交由仓库工作流。

[DONE]
