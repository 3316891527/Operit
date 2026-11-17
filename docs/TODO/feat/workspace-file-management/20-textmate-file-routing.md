---
title: TextMate 文件路由与 ActionScript 接入核对
status: pending_validation
fork: https://github.com/3316891527/Operit
branch: feat/workspace-file-management
---
# TextMate 文件路由与 ActionScript 接入核对

[返回计划](index.md)

## 原有行为与目标

TextMate 资源目录有 267 个文件，其中 260 个是语法 JSON，包含 242 个语言定义和 18 个注入定义，其余七个为目录、说明和许可证。已有 `actionscript-3.json`，`.as` 已关联 ActionScript 3，并已有 `actionscript`、`as3` 别名。

工作区打开路由另外维护 76 个文本后缀，没有使用高亮目录登记的 844 个后缀、284 个特殊文件名和三个文件名前缀。ActionScript 等语言可以高亮，却不能通过默认文件路由进入编辑器。

本轮统一文件关联与默认文本入口，复用现有 ActionScript 语法并登记 `as` 别名，不重复引入同一语言资源。媒体 MIME 与源码后缀重叠时，读取有界内容样本区分文本与二进制。归档、媒体和办公文档仍使用对应入口，文本读取继续执行大小限制和内容验证。

## 实施清单

- [x] 默认文本打开规则复用 TextMate 目录，仅单独保留普通文本格式
- [x] 已登记源码与媒体 MIME 冲突时按内容分类
- [x] 接通 ActionScript 的 `as` 别名与注释操作
- [x] 补充全目录关联、真实 ActionScript 高亮和文本／二进制分类回归源码
- [x] 整理静态核对与原分支、原 PR 的交付内容

## 验证边界

本地仅进行资源目录、生成一致性、语法和差异静态核对。JVM 回归与 Android 构建由远程工作流执行，运行记录建立后停止查询进度。真实设备打开、编辑和多主题显示仍需验收。


## 实现与目录补全

默认文本路由直接查询 TextMate 生成目录，移除原来的编程语言后缀副本，仅保留十种没有语法定义的普通文本后缀。普通列表、归档提取和文件链接共用这套路由。实际读取继续使用 `text_only` 内容验证与已有大小限制。

补齐十一种已有语言的独立文件关联：AutoHotkey 2、DAX、Hurl、Logo、MDC、Narrat、qmldir、QSS、Rel、systemd 和文言。九种按语法来源的固定提交读取扩展清单，在 `associationsSource.supplements` 记录 URL、Git blob SHA 和登记内容；Logo 的 `.logo`、`.lgo` 与文言的 `.wy` 记录为本地关联。`.service` 保留原 D-Bus 关联，systemd 通过 `.timer`、`.socket`、`.service.in` 等后缀识别；`.ahk` 和 `.md` 保持原有选择。

目录从 844 个后缀、284 个特殊文件名补至 881 个后缀、285 个特殊文件名，另有三个前缀，共 1169 条关联。其余七种为嵌入或共享格式的规则变体，通过所属语言使用。语法仍为 260 份、语言定义仍为 242 份，资源目录仍为 267 个文件。

源码关联与媒体 MIME 冲突时，复用跨环境文件工具读取最多 512 字节样本，文本进入编辑器，二进制保留媒体 MIME。原来仅针对 `.ts` 的分类推广到其他已登记源码关联。SVG 和办公文档继续使用现有预览入口。

## 静态核对与回归源码

新增七项 JVM 回归源码，覆盖整份实际目录的默认文本路由、独立语言关联完整性、ActionScript 后缀与四个别名及注释规则、真实 ActionScript 分词，以及媒体 MIME 冲突中的文本和二进制边界。既有 TypeScript 与 MPEG-TS 回归改用共同分类入口。

资源结构、来源校验、生成一致性、八个 Kotlin 文件语法和差异空白静态核对通过。没有新增运行时依赖或翻译键。仓库卫生、文档链接与本地化检查由提交后记录维护；远程构建和测试链接维护在同一 PR，尚不作为通过结论。

[DONE]
