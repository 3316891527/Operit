---
fork: https://github.com/3316891527/Operit
status: preparing-authorized-publication
version: 1.12.2+28
versionCode: 84
---

# 1.12.2+28 正式发行

维护者已确认 #238、#239、#241、#242、#243、#244 的初步设备测试通过，并明确同意发布本轮版本。#240 没有独立可复现路径，终端观察的长期内存、磁盘 I/O 与快照增长仍待后续实测。本次不追加快照压缩、对象打包或保留策略，也不把未测性能写成已验证收益。

## 固定候选

产品候选为 `4f8990e3e3b6b00467c453393eb73d869058ec7c`，包含 #238–#244 和终端基线内存修复，排除 #105。

- [Debug Build #293](https://github.com/3316891527/Operit/actions/runs/45088236846)：success
- [Android Tests #756](https://github.com/3316891527/Operit/actions/runs/45088236873)：success
- [PR Check #549](https://github.com/3316891527/Operit/actions/runs/45088244086)：success

为保留正式分支的合并历史，先合入原 main 提交 `6355bbb5a181289e90498d4a33732db073c84894`；已确认合并后的文件树与已验收候选完全一致。首次准备仅调整版本号、版本码和发行文档。该轮发行随后被维护者取消，追加改动见下文。

## 发布步骤

1. 版本由 `1.12.2+27 / 83` 升至 `1.12.2+28 / 84`，核对来源祖先、产品文件及更新日志。
2. 更新集成 PR #245 并以保留父提交的方式合入 dev，再建立 dev 到 main 的发行同步 PR。
3. 从核对后的 main 提交派发 Build，选择 `:app:verifyCiPreviewRelease`、`publish_release=true`、`previous_tag=v1.12.2+27`。
4. 沿用 Kong 固定包名和签名，工作流校验非调试标记、版本递增、资源一致性及增量补丁还原后，发布到 Operit-Kong-patch。
5. 登记工作流启动链接后停止查询进度；派发成功与发行完成分别记录。

本轮关闭 `complete_repository_migration` 和 `cleanup_merged_branches`，不复用旧版本的分支清理计划。公开发行说明为 `ci/distribution/updates/releases/1.12.2+28.md`，设备诊断和用户私有数据不提交。

## 验证边界

正式签名构建是完整发行工作流，不能由 Debug 产物替代。本轮本地检查聚焦版本、发行元数据和文档；既有完整产品回归对应上述候选。发行成功以公开的 APK、资源、补丁附件及工作流结果为准，长时间设备性能验收另行记录。


本地发行回归共 65 项通过。首次发行准备已核对版本之外的产品文件保持已验收候选内容，全部来源提交保留且 #105 未纳入。

[DONE] 版本与发行文档准备、本地发行回归；实际公开发行仍以工作流为准。


## 取消后追加 Waifu 过程展示

首次正式发行 [Build #296](https://github.com/3316891527/Operit/actions/runs/45100266870) 已通过强制取消接口结束，最终状态为 completed/cancelled；未查到 +28 发行条目。维护者授权直接在 dev 增补改动并重新发行，沿用尚未公开的版本号 1.12.2+28 / 84。

1. [DONE] Waifu 分句与展示保留 think、thinking、tool、tool_result 块，生成中的过程可预览，取消时保留已经生成的内容。
2. [DONE] 移除聊天时间线和消息样式的 Waifu 强制隐藏，思考显示沿用通用设置，工具内容交给现有渲染器。
3. [DONE] 同步现有清理、快照和流式断言，公开说明追加行为；本轮不单独安排设备验收，不把原候选验收记作追加改动已通过。
4. 将追加提交推送 dev 并同步 main，从准确 main 提交重新发起正式发行，登记工作流启动后停止查询进度。
