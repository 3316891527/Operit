---
fork: https://github.com/3316891527/Operit
branch: ci/chat-ui-waifu-release-r8
---

# 聊天界面与 Waifu Markdown 的 Kong 共存版

本分支以 [PR #1372](https://github.com/AAswordman/Operit/pull/1372) 的 `ba114e73890e2948b71481c39e798c37a1ee8711` 为基线，合入 [PR #1338](https://github.com/AAswordman/Operit/pull/1338) 的 `8e2ea297c7991baba5e2f95590bd0faba1d5ce65`，将聊天界面重构与 Waifu 流式 Markdown 修复放到同一份共存产物中。

## 安装身份与构建配置

CI 构建的 application ID 为 `com.ai.assistance.operit.kong`，后缀为 `.kong`。桌面名称为 `Operit Kong`，APK 文件名为 `app-kong.apk`，可与包名为 `com.ai.assistance.operit` 的正式版分别安装。主 Manifest 中 Provider 的 authority 使用 `${applicationId}`，随共存包名变化。

[共存资源目录](../../../app/src/kong/) 提供独立的桌面名称与静态快捷方式。语音悬浮窗、设置和数据救援的快捷方式均指向共存包，代码 namespace 和组件类名继续使用原有名称。

通过现有 [Android Build 工作流](../../../.github/workflows/android-build.yml) 派发 `:app:bundleRelease`。本集成分支在 `GITHUB_ACTIONS=true` 时将 Release 变体配置为 Kong 共存包，使用 Android 调试证书签名并跳过正式版密钥轮换。构建明确设置 `isDebuggable=false`；`isMinifyEnabled` 和 `isShrinkResources` 均为 `false`。`bundleRelease` 同时依赖 APK 构建和验证任务，生成 `app-kong.apk` 与使用共存包名的 `app-release.aab`。

共存版使用独立安装身份，不用于覆盖正式版。不同 CI 运行之间不保证使用同一张预览签名证书。普通本地 Release 构建继续使用原有正式包名和签名配置。

## 产物核验

验证任务检查 `app-kong.apk` 存在且非空，通过 `apksigner verify` 检查签名，再使用 `aapt2 dump badging` 核对实际包名为 `com.ai.assistance.operit.kong` 且没有调试标记。核验输出写入 `app/build/reports/kong-preview/apk-badging.txt`，随现有工作流报告上传。

签名与产物核验命令使用 Gradle `providers.exec` 执行，并在任务内读取执行结果或标准输出；命令非零退出会使任务失败。该方式避免已弃用的 `project.exec`，并直接读取 `aapt2` 输出，避免脚本的 `java` 扩展遮蔽 `java.io` 包而导致名称解析失败。

成功构建的 APK 和 AAB 位于 `operit-android-<run_number>` 产物，报告位于 `operit-android-reports-<run_number>` 产物。

## 验证记录

两个 PR 已无冲突合并。日语孤立条目 `follow_chat_model_for_all_functions` 已删除，修复 Release 的 `ExtraTranslation` 致命检查。

提交前静态核对共存配置、XML、快捷方式目标、动态 Provider authority、实际产物验证逻辑、最终差异和文档链接。本地未运行 Android 构建或测试，编译和产物身份核验由仓库工作流完成，结果与设备共存安装效果待确认。

实现已完成。[DONE]
