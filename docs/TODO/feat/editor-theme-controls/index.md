---
repository: https://github.com/3316891527/Operit
branch: feat/editor-theme-controls
status: implemented-awaiting-ci-and-device-validation
---

# 平台编辑器主题控制

现有 operit_editor 已使用单一结构化命令入口，主题偏好尚未接入。将日常外观调整加入宿主命令，沿用已有角色、角色组主题与全局用户资料保存机制。

## 确定范围

主题配色、字体与头像、背景、消息外观、消息信息显示。字段按 colors、fonts、avatars、background、messages、info 分组按需查询。语言、全局显示行为、全局布局、输入框与侧边栏、主题工作室和玻璃细节参数不属于主题命令范围。后续迭代补充独立 Waifu 命令，覆盖既有设置页的九项配置。

## 实施步骤

1. [命令与保存](01-command-and-storage.md)：发现目标、查询字段、读取版本和增量修改，保持脚本单一入口
2. [资源与并发](02-resources-and-drafts.md)：持久导入图片、视频和字体，校验资源，避免主题草稿覆盖命令修改
3. [帮助、Waifu 与命令分组](03-help-waifu-and-packages.md)：精简说明，加入独立 Waifu 配置命令并整理主题命令包

## 验证与交付

新增严格字段校验、版本、命令解析和三方合并的 JVM 回归源码；资源导入与持久保存补 Android 回归源码。静态检查与编译、运行结果分别记录。远端发起 assembleDebug 和 Android Tests，启动登记后不轮询进度。

四项宿主命令、资源导入与草稿并发处理已实现，脚本继续只公开 args 入口。首轮新增 73 项回归源码：53 项 JVM、20 项 Android；后续 Waifu 与分组迭代新增 41 项：24 项 JVM、17 项 Android，均未在本地执行。已完成源码审查，最终交付执行严格 UTF-8、Kotlin 语法树、JavaScript 语法、文档 JSON 与链接、生成脚本一致性和差异空白静态检查。

编译、测试执行及设备显示仍待远端 CI 和后续验收，不能把静态检查视为这些项目已通过。

[DONE]
