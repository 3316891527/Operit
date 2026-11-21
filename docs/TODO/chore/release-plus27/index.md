---
fork: https://github.com/3316891527/Operit
branch: test/release-27-integration
status: preparing-authorized-publication
version: 1.12.2+27
versionCode: 83
---

# 1.12.2+27 正式发行

维护者已确认子代理基本测试通过，并明确要求先发布本轮已验收功能，随后再做体验优化；本轮授权包括更新日志、合入 dev、同步 main、正式发行、同轮离线资源和已合入分支清理。Waifu 思考与工具卡片仅有设计稿，本次没有实现或纳入发行。

## 固定范围与证据

产品提交为 7263e6d3f4fe898a97507a53c79ed109ab0e0bed，包含 #222～#235 及子代理预览修复。该提交的 Debug Build #280 和 Android Tests #744 均已完成且结论为 success；维护者另已完成基本设备验收。此前主分支与 dev 文件树一致，本轮先保留主分支发行合并历史，核对产品树未变，再将版本由 1.12.2+26 / 82 升至 1.12.2+27 / 83。

面向使用者的[更新日志](../../../../ci/distribution/updates/releases/1.12.2+27.md)随源码保存，并用于正式 Release 正文。#105 的更新日志归档分支继续保留，未纳入产品树。

## 正式发布

1. 更新集成 PR #236，以保留父提交的方式合入 dev，核对全部来源 HEAD 都已包含
2. 建立 dev 到 main 的发布同步 PR，合并后核对两分支文件树一致
3. 从 main 固定提交派发 Build，gradle_task=:app:verifyCiPreviewRelease、publish_release=true、previous_tag=v1.12.2+26、complete_repository_migration=false、cleanup_merged_branches=true
4. 沿用 com.ai.assistance.operit.kong 与固定签名，校验非调试 APK、版本递增及 +26 到 +27 补丁还原；公开到 Operit-Kong-patch
5. prepare_signing.py 调用 prepare_current_resources.py，以本轮已构建的白名单扩展和模板生成新资源目录；终端与编辑器扩展及公共类型声明更新均进入源码指纹。资源与 APK 内目录核对后先公开 Operit-Kong-resources，再公开应用 Release
6. 正式 Release 及全部附件核验成功后，依据 branch-cleanup-plan.json 清理本轮 15 个来源与集成分支。清理器新增 test/ 前缀支持，继续保护 main、dev、文档分支、开放 PR 和未合入的新提交，删除时固定分支头租约

更新资源由同一签名构建自动准备和发布，不直接把旧资源标签改成 +27，不改写已公开资源附件。派发成功不代表发行成功；启动记录保存后不持续轮询。

## 本轮验证

版本及发行配置静态检查、发行脚本回归和文档检查在提交前执行。完整 Android 产品回归已通过本轮产品提交的 #280 / #744；正式签名、资源发布和补丁校验由发行工作流执行，结果以实际公开的附件与运行报告为准。
