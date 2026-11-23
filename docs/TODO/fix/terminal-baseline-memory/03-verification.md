# 验证与交付记录

## 本地验证

- 42 项独立 JVM 回归通过，使用真实 SQLite JDBC 驱动执行生产索引 SQL，覆盖扫描、迁移、冷启动覆盖、目录改名、跨页事件来源、查询缓存边界、事务回滚和资源释放失败。
- 生产日志、存储、扫描器、SQLite 适配器、迁移、观察根和目录观察器共 11 个文件联合类型检查通过。Android 服务、挂载映射与差异统计使用仅供编译的临时支架，不能替代完整 Android 构建。
- 合成 303,482 条较长路径，在独立 JVM 的 64 MiB 堆上限下完成写入、查询、局部删除和重开检查，查询缓存不超过 256 条。该结果只覆盖索引，不包含 Android 界面、词典、聊天内容和目录监听器。
- 新增 4 项 Android 设备测试，覆盖真实旧清单迁移和历史删除期间的快照保留，尚未在本机执行。

复现核心单测可运行：

```bash
./gradlew :app:testDebugUnitTest --tests '*TerminalObservedFilesTest' --tests '*TerminalObservationIndexTest' --tests '*TerminalPendingEventsTest' --tests '*LegacyTerminalBaselineTest' --tests '*TerminalChangeOriginTest'
```

## 组合验证

修复以排除 #105 的 #238–#244 组合提交为基线，保留原 PR 的来源和独立提交。正式版本仍未提升，组合候选用于 assembleDebug、JVM 测试与设备测试 APK 构建。

完整 CI 结果、安装后的内存曲线和设备事件测试仍待确认。设备诊断、真实路径和堆快照仅保存在本地，不随代码提交。
