---
fork: https://github.com/3316891527/Operit
branch: docs/organize-editor-highlighting
status: awaiting-dev-debug-acceptance
version: 1.12.2+26
versionCode: 82
---

# 1.12.2+26 集成与 Debug 验收

按维护者要求将所有开放 PR 合入 dev，排除更新日志归档 PR #105。A2UI 组件样式本轮暂不调整。按维护者最新要求，本阶段只从 dev 派发 assembleDebug，publish_release=false。等待维护者验收合格并另行通知后，才同步 main 和发布正式 26；构建成功不触发后续发布。

## 集成范围

- [#212](https://github.com/3316891527/Operit/pull/212)：插件、模块与 WebChat 的源码目录重构
- [#213](https://github.com/3316891527/Operit/pull/213)：耗时单位与舍入边界统一
- [#214](https://github.com/3316891527/Operit/pull/214)：新建聊天动画请求生命周期修复
- [#215](https://github.com/3316891527/Operit/pull/215)：市场与角色卡的主题色、菜单样式修复
- [#216](https://github.com/3316891527/Operit/pull/216)：工作区文件管理、编辑器、变更审阅与 HTML 打包

五个 PR 已依次合入 dev。#215 的 TODO 索引冲突保留双方新增条目；#216 新增的 11 个图标迁入 modules/material-icons-subset。远端 dev 的 fb8c496633fe825472e9d2b3b8ddc1813a029fb6 与独立联合预演的代码树完全一致，#105 独有提交未纳入。

旧资源 schema 的 examples 与 tools/example_packages 是逻辑路径，继续映射到新的物理目录，保持已发布离线资源摘要可验证。Gradle、Actions、图标脚本及终端命令使用重构后的物理路径。

## 验证与发布顺序

1. 版本名 1.12.2+26、versionCode 82 和[发行说明草稿](../../../../ci/distribution/updates/releases/1.12.2+26.md)已由 #217 合入 dev。
2. 将[高亮开发说明](../../../doc-src/dev-core/editor-highlighting/editor-highlighting.md)、同目录 Schema 与[示例](../../../doc-src/dev-core/editor-highlighting/examples/README.md)统一归档到 dev-core/editor-highlighting，示例直接位于 examples 子目录，移除根目录旧位置并修正引用。
3. 检查本轮文档链接、JSON 内容与迁移前后一致性，再将文档整理合入 dev。
4. 只对新的 dev 提交派发 assembleDebug，publish_release=false，不另行派发 Android Tests。此前已发起的测试与构建属于迁移前提交，结果按各自运行记录保留。
5. 维护者验收 Debug；同步 main、签名构建和公开发行均等待新的明确通知。Android 设备交互验收与编译结果分别记录。

## 发行参数

发行仓库为 3316891527/Operit-Kong-patch，离线资源仓库为 3316891527/Operit-Kong-resources。保留 Kong 包名与既有固定签名，基础正式发行已核实为 v1.12.2+25。complete_repository_migration 和 cleanup_merged_branches 均保持 false。

本轮只整理开发资料并派发 dev Debug，正式发行尚未获本阶段授权。高亮资料与示例归档 [DONE]。
