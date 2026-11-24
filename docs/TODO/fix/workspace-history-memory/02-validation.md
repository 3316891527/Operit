---
fork: https://github.com/3316891527/Operit
status: 已记录，远端验证待确认
---

# 回归与验证范围

## 已取得的结果

在 Android 手机上的 Ubuntu 环境中，使用 OpenJDK 17 ARM64、Kotlin 2.0.21、kotlinx.serialization 1.6.2、Gson 2.11.0、SQLite JDBC 3.46.1.3 和 java-diff-utils 4.12，直接编译选定的生产文件与 JUnit 回归。15 个套件共 157 项，0 失败。最终轻量审阅开始时核对全部 39 个编译输入的 SHA-256，18 个生产文件、16 个测试或测试支持文件及 5 个验证桥接文件当时均未变化。随后将首页产出入口改为 LIMIT 1 查询，新增聊天隔离与等时间排序回归，并补齐可保存文件状态的恢复淘汰顺序；这些收尾修改没有再运行本地回归，交由远端 CI。机器记录同时保留已验证输入哈希及收尾后的变化清单。

[机器可读验证记录](validation.json) 保留仓库源码的相对路径和哈希，并列明桥接文件的名称、哈希与检查范围。生产文件通过真实文件、SQLite 和内容分卷执行；挂载路径、Android 日志和原子文件等平台依赖使用验证桥接，公开 `android.system` SDK 类用于适配器兼容检查。

新增回归覆盖操作增量合并与恢复原内容、同时间戳分页、共享存储别名与远端身份、Unicode 相对路径搜索、完整对话中选轮次、逐元素迁移和损坏取消恢复、事务失败后的缓存及尾部恢复、128 个对象批次边界、基线租约与锁顺序、失败内容批次禁止发布、删除确认后的新增操作、跨对话和基线共享引用、子树扫描与中间符号链接，以及视图选择容量限制。

本轮运行的套件包括 TerminalObservedFilesTest、TerminalObservationIndexTest、TerminalChangeOriginTest、LegacyTerminalBaselineTest、TerminalPendingEventsTest、SnapshotPackStoreTest、WorkspaceSnapshotReaderTest、WorkspaceDiffPolicyTest、WorkspaceReviewStateTest、WorkspaceDiffTest、WorkspaceHistoryIndexTest、WorkspaceHistoryBatchTest、WorkspaceHistoryStoreTest、WorkspaceFileObservationTest 和 WorkspaceIndexedScanTest。

## 联合类型检查的限制

22 个生产文件曾通过独立联合类型检查，包含真实 Store、Journal、Monitor、Recorder、GitCommand 与底层 SQL。Android 服务、工具读取、日志、挂载和部分差异依赖使用类型脚手架。检查完成后仍有存储和路径代码修改，具体清单见机器记录；不能将这次结果视为最终版本的完整编译通过。

Compose 的 WorkspaceChangesScreen 和 WorkspaceChangeRecordActions 未包含在该检查中。界面翻页、跨页导航、单文件菜单和最近视图状态做了源码审阅，尚无完整 Android/Compose 编译或设备视觉证据。回归结果也不等于应用在长任务、模型并发和宿主原生内存条件下的运行结论。

## 标准远端验证

从本轮分支通过 Build 工作流派发 `assembleDebug`。Android Tests 工作流执行 `:app:testDebugUnitTest`，并编译 `:app:compileDebugAndroidTestKotlin` 与 `:app:compileDebugAndroidTestJavaWithJavac`；`build_device_tests=false`，不把仪器测试编译描述为设备测试执行成功。

手机本地不再启动新的编译、测试或规模测量。远端工作流确认启动后仅登记链接，完整构建、测试和新 APK 结果以对应运行最终产物为准。
