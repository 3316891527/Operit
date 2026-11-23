---
fork: https://github.com/3316891527/Operit
status: preparing-authorized-publication
---

# 1.12.2+28 开放 PR 组合验证

## 目标

将已开放且用户要求纳入的 PR 在独立分支组合，检查最终代码是否可以一起构建和运行测试，为 +28 发布提供同一提交的依据。单个 PR 的绿灯不能替代组合验证。

基线 dev：`d9610f9bc857ab4edd9f0e7f3c96277a234affff`。集成分支：`ci/release-28-integration`。

## 纳入范围

- [#238 fix(workspace): exclude restricted paths from terminal observation](https://github.com/3316891527/Operit/pull/238)：`cb31d5f2f084f0e0c7f076f0d9e2deaa2535b143`
- [#239 fix(files): open log and common text attachments as text](https://github.com/3316891527/Operit/pull/239)：`d91c2696d398513b587ef0d75c96abc41be0542b`
- [#240 fix(subagent): recover final reports and preserve execution outputs](https://github.com/3316891527/Operit/pull/240)：`b37ca493bafa9a7884cf455896230ccaccb12374`
- [#241 fix(chat): prevent invalid thinking level writes and crashes](https://github.com/3316891527/Operit/pull/241)：`dbe7bf93b9bb04698062aff4e18d13773de928eb`
- [#242 fix(backup): rebind theme scripts when restoring snapshots](https://github.com/3316891527/Operit/pull/242)：`1f5ec3b89ec2dd570394f1488826c35ec1655e02`
- [#243 fix(subagent): avoid full history loads during delivery recovery](https://github.com/3316891527/Operit/pull/243)：`4486b78d900df9951c58538cc70c19aa1b11e52e`
- [#244 feat(terminal): compact build progress in model context](https://github.com/3316891527/Operit/pull/244)：`a05fed46fab1f6c3ed0a99ed1c28cee9b74e63d0`

明确排除 #105。保留各 PR 的独立提交来源；当前步骤只产生测试候选，不合并 dev/main，不改正式版本号，不发布更新。

## 验证与交付

[组合候选自动验证](01-candidate-ci.md)为专用组合分支增加推送入口；Build 执行 `assembleDebug`，Android Tests 同时构建设备测试 APK 对。PR 为 [#245](https://github.com/3316891527/Operit/pull/245)，工作流以实际推送后的准确 head SHA 登记。

- 顺序合并固定 PR 提交，记录并处理实际冲突，不追加无关业务改动
- 确认最终提交包含全部七个来源，差异无冲突标记且通过空白与编码检查
- 对同一最终提交启动 assembleDebug 和 Android Tests，构建设备测试 APK
- 登记工作流已启动后停止查询进度，CI 通过和设备实际验收分别记录
- 设备重点验收长历史总结后恢复、思考设置、主题脚本导入、终端输出与原文入口

## 当前状态

固定产品候选 `4f8990e3e3b6b00467c453393eb73d869058ec7c` 的 Debug Build #293、Android Tests #756 和 PR Check #549 均已通过。维护者已确认 #238、#239、#241、#242、#243、#244 初步设备验收通过；#240 暂无单独可复现路径，观察基线的长期内存与 I/O 表现仍待实测。

组合完成后，#242 和 #243 只在缺陷计划索引发生冲突，处理时保留双方链接；业务代码均按 Git 合并结果保留，没有额外手工实现。已核对子代理报告恢复、持久化标记查询、执行归属与终端展示投影同时存在。

静态验收确认七个来源提交全部是候选祖先，#105 不是候选祖先，单一 PR 拥有的文件与来源提交逐个对齐。组合 Kotlin 语法、严格 UTF-8、冲突标记与差异空白检查通过后，构建和测试统一针对最终提交启动。

组合验收阶段保持基线版本 1.12.2+27、versionCode 83。维护者确认后进入[正式发行步骤](02-publication.md)，将版本升至 1.12.2+28、versionCode 84，沿用固定签名并从 main 启动发行工作流。


## 终端基线内存修复

原组合基线之后追加[终端基线磁盘索引修复](../../fix/terminal-baseline-memory/index.md)。保留全盘终端变更观察，并将基线、通知队列和扫描租约迁至磁盘；候选提交以交付记录及实际 CI 的 head SHA 为准。正式版本号与发布状态见上述发行步骤；发行完成以工作流结果和公开附件为准。


## Waifu 过程展示追加改动

维护者取消首次发行 Build #296 后，要求 Waifu 模式不再过滤思考与工具调用，并授权直接在 dev 修改后重新发行，无需单独设备验收。移除消息分句与界面的过程过滤，保留思考、工具调用和结果 XML；通用展示设置继续生效。现有断言同步新行为，正式发行按追加改动后的 main 提交启动。具体步骤见[正式发行记录](02-publication.md)。
