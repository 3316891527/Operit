---
fork: https://github.com/3316891527/Operit
branch: ci/prepare-release-26
status: validating
version: 1.12.2+26
versionCode: 82
---

# 1.12.2+26 集成与发行

按维护者要求将所有开放 PR 合入 dev，排除更新日志归档 PR #105。A2UI 组件样式本轮暂不调整。dev 的 Android Tests 和 assembleDebug 全部通过后，才同步 main 并执行正式发行。

## 集成范围

- [#212](https://github.com/3316891527/Operit/pull/212)：插件、模块与 WebChat 的源码目录重构
- [#213](https://github.com/3316891527/Operit/pull/213)：耗时单位与舍入边界统一
- [#214](https://github.com/3316891527/Operit/pull/214)：新建聊天动画请求生命周期修复
- [#215](https://github.com/3316891527/Operit/pull/215)：市场与角色卡的主题色、菜单样式修复
- [#216](https://github.com/3316891527/Operit/pull/216)：工作区文件管理、编辑器、变更审阅与 HTML 打包

五个 PR 已依次合入 dev。#215 的 TODO 索引冲突保留双方新增条目；#216 新增的 11 个图标迁入 modules/material-icons-subset。远端 dev 的 fb8c496633fe825472e9d2b3b8ddc1813a029fb6 与独立联合预演的代码树完全一致，#105 独有提交未纳入。

旧资源 schema 的 examples 与 tools/example_packages 是逻辑路径，继续映射到新的物理目录，保持已发布离线资源摘要可验证。Gradle、Actions、图标脚本及终端命令使用重构后的物理路径。

## 验证与发布顺序

1. 准备版本名 1.12.2+26、versionCode 82 和[发行说明](../../../../ci/distribution/updates/releases/1.12.2+26.md)，合入 dev。
2. 对最终 dev 执行仓库门禁、Python 和 Node 回归；从 dev 派发 Android Tests 与 assembleDebug，跟进完整结论并修复真实失败。
3. JVM、终端命令和构建结果按工作流记录；Android 设备界面回归须单独执行，不以静态解析或源码存在替代。
4. 全部指定验证通过后，通过维护者同步 PR 将 dev 合入 main，并核对代码树与已测试的 dev 一致。
5. 从 main 派发 Build，使用 :app:verifyCiPreviewRelease、publish_release=true、previous_tag=v1.12.2+25 和非空中文发行说明。
6. 核验正式 Release、完整 APK、增量包、更新元数据和新离线资源的实际发布结果。

## 发行参数

发行仓库为 3316891527/Operit-Kong-patch，离线资源仓库为 3316891527/Operit-Kong-resources。保留 Kong 包名与既有固定签名，基础正式发行已核实为 v1.12.2+25。complete_repository_migration 和 cleanup_merged_branches 均保持 false。

当前记录集成和发行准备，测试及正式公开状态以实际工作流与 Release 收据为准。版本准备 [DONE]。
