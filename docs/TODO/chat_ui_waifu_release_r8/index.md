---
fork: https://github.com/3316891527/Operit
branch: ci/chat-ui-waifu-release-r8
---

# 聊天界面与 Waifu Markdown 的 Release 预览

本分支以 [PR #1372](https://github.com/AAswordman/Operit/pull/1372) 的 `ba114e73890e2948b71481c39e798c37a1ee8711` 为基线，合入 [PR #1338](https://github.com/AAswordman/Operit/pull/1338) 的 `8e2ea297c7991baba5e2f95590bd0faba1d5ce65`，将聊天界面重构与 Waifu 流式 Markdown 修复放到同一份预览产物中。

## 构建配置

Release 明确设置 `isDebuggable=false`，`isMinifyEnabled` 和 `isShrinkResources` 均设为 `false`，沿用基线分支关闭代码缩减、混淆与资源压缩的构建配置。验证任务不再要求或复制 R8 映射文件。

通过现有 [Android Build 工作流](../../../.github/workflows/android-build.yml) 派发 `:app:bundleRelease`。本集成分支在 `GITHUB_ACTIONS=true` 时使用 Android 调试签名并跳过正式 APK 的密钥轮换签名，`bundleRelease` 同时依赖 Release APK 构建与验证任务，产出 `app-release.apk` 和 `app-release.aab`。预览签名与正式版不同，无法覆盖正式版；不同 CI 运行之间也不保证使用同一张调试证书。普通本地 Release 构建继续使用原有正式密钥配置及轮换签名。

验证任务检查实际 APK 文件存在且非空，并使用 Android build-tools 的 `apksigner verify` 检查 APK 签名，再通过 `aapt2 dump badging` 确认包名为 `com.ai.assistance.operit` 且没有调试标记。核验输出写入 `app/build/reports/release-preview/apk-badging.txt`，随现有工作流报告上传。成功构建的 APK 和 AAB 保存在 `operit-android-<run_number>` 产物中，报告保存在 `operit-android-reports-<run_number>` 产物中。

## 验证记录

两个 PR 已无冲突合并，功能文件与来源提交保持一致。删除仅存在于日语、且无默认资源或代码引用的 `follow_chat_model_for_all_functions`，修复 Release 的 `ExtraTranslation` 致命检查。提交前静态核对 Release 配置、移除的 R8 校验、最终差异、文档链接及既有工作流入口。本地未运行 Android 构建或测试，编译和产物签名验证由仓库工作流完成，构建结果及设备运行效果待确认。

实现已完成。[DONE]
