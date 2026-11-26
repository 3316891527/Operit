---
repository: https://github.com/3316891527/Operit
branch: feat/editor-theme-controls
status: implemented-awaiting-ci-and-device-validation
---
# 帮助入口、Waifu 配置与命令分组

## 原状与目标

编辑器包与工具说明重复列举宿主命令，主题命令实现集中在 commands 根目录；Waifu 使用独立的偏好存储，目前未接入结构化命令。

收简中英文元数据，将具体能力和参数交给宿主 Help。新增独立 waifu targets/fields/show/set，覆盖设置页的九个持久字段，沿用调用对话、角色卡和角色组的归属规则。

## 实施边界

主题命令放入 core.tools.commands.theme，字段校验、资源导入与版本计算归入 theme.editor。公共工具名、CLI、既有持久化键和 UI 会话保持原有接口。命令目标解析供主题和 Waifu 共用；数据与 UI 不依赖命令实现。

Waifu 更新要求先读取同一目标版本，只修改显式字段，在同一次 DataStore 事务中检查版本并更新目标存储。目标为当前角色或组时同步运行值，其他目标不影响当前聊天。设置页支持的开关、字符延迟、合并等待时间和两段提示词通过 Help 查询，未知字段、类型错误、范围错误和过期版本明确报错。

## 验证与交付

检查三份编辑器元数据一致、两份 JS 一致、UTF-8、Kotlin 语法与引用、文档和 git diff。为新增持久化边界与命令合同补充针对性回归源码。代码完成后推送现有分支并更新 PR #268，发起远端 assembleDebug 与测试工作流，确认启动后不轮询进度。编译与测试结果据实记录。


## 已完成

三份编辑器中英文元数据已同步收简，主题入口与 theme.editor 编辑实现分包，主题及 Waifu 共用目标解析。Waifu 九字段已接入独立四条命令，目标存在性在切换锁内复验，版本校验与补丁合并在同一事务执行。结果生成不再读取偏好，保存后的独立校验失败仍保留 saved=true。

角色激活命令改走统一切换入口并清除旧组；清除卡片绑定时按实际剩余目标同步 Waifu 运行值，避免当前目标与运行配置不一致。

新增回归源码 41 项，其中 JVM 24 项、Android 17 项，覆盖严格参数、版本、并发提交、卡片与角色组隔离、运行值同步、目标删除及角色切换。Android 用例设置协程超时并恢复测试前状态。本地未执行这些用例，也未编译或构建；已完成 UTF-8、Kotlin 语法树、JavaScript 语法、元数据一致性、文档 JSON 与链接及差异空白静态检查。远端使用 assembleDebug 与 Android Tests，后者同时生成应用和仪器测试 APK；设备执行与显示仍待验收。

[DONE]
