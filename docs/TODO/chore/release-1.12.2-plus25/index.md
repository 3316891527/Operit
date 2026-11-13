---
repo: https://github.com/3316891527/Operit
branch: chore/release-1.12.2-plus25
status: validating
---

# 1.12.2+25 发行准备

将主题工作室媒体资产上限由 48 调整为 96，整合当前开放的功能和修复 PR，并准备 `1.12.2+25`、versionCode 81。更新日志归档 PR #105 按维护者要求排除。

## 整合范围

- [#195](https://github.com/3316891527/Operit/pull/195)：主题文字颜色、复制预览边缘滚动与 A2UI 即时渲染
- [#196](https://github.com/3316891527/Operit/pull/196)：发送前图片标注编辑
- [#197](https://github.com/3316891527/Operit/pull/197)：删除空 DragonBones 模型占位
- [#198](https://github.com/3316891527/Operit/pull/198)：思考规则完整保存与档位一致性
- [#199](https://github.com/3316891527/Operit/pull/199)：Waifu 仅展示正文
- [#200](https://github.com/3316891527/Operit/pull/200)：OpenAI 绘图脚本与资源更新一致性
- [#201](https://github.com/3316891527/Operit/pull/201)：主题工作室媒体资产上限 96

独立候选从 `dev` 的 `9532d2664f921d1f138813a22c69f26c3de457b9` 建立，保留全部来源 PR head，并接入 +24 已发布的 main 历史。#196 的九份、#198 的两份语言 XML 位置冲突按资源键保留双方新增内容，解析和键完整性核对已完成。原工作树的本地改动保留。

## 发布参数

[发行说明](../../../../ci/distribution/updates/releases/1.12.2+25.md)与[发布工作流](../../../../.github/workflows/build.yml)使用以下参数：

- 版本 `1.12.2+25`，versionCode 81
- 包名 `com.ai.assistance.operit.kong`，沿用既有固定签名
- 增量基线 `v1.12.2+24`，发行仓库 `3316891527/Operit-Kong-patch`
- 签名验证任务 `:app:verifyCiPreviewRelease`
- 候选验证使用 `publish_release=false`
- 正式发行引用 `main`，单独设置 `publish_release=true`
- `complete_repository_migration=false`，`cleanup_merged_branches=false`

## 验证与交付

1. [x] 完成媒体上限调整及 96 项允许、97 项拒绝的边界回归代码，来源 PR #201 的 Candidate checks 与 Android JVM tests 已通过。
2. [x] 将七个 PR 的完整 head 纳入独立候选，保留 main 历史，准备版本和发行说明。
3. [ ] 完成候选的仓库卫生、本地化、Markdown 链接和差异检查；对发布准备 PR 要求完整 Android 构建及 JVM 测试。
4. [ ] 将验证通过的整合候选合入 dev，核对七个来源 PR 的远端合并状态，保持 #105 开放。
5. [ ] 通过独立维护者发布同步 PR 更新 main，并取得固定签名候选 APK。
6. [ ] 正式发行另行执行，记录公开 Release、完整 APK、增量包与元数据的核验结果。

各来源 PR 的独立 CI 结果不替代整合候选验证。图片编辑的缩放坐标、旋转后马赛克对齐、大图内存和系统返回等设备交互仍保留原有待验收边界；不将静态检查或 CI 通过记为实机验收。

[绘图资源修复](../../fix/openai-draw-resource-updates/index.md)已单独公开并核验下载；这不代表 +25 APK 已公开。版本准备、源码合并、构建成功和正式发行分别记录。
