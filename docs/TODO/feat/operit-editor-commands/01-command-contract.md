# 宿主命令与调用契约

现有编辑器把手册、多工具声明和 JavaScript 业务组合放在同一大文件中，主入口本身只返回“手册已加载”。本次将入口改为命令数组，由宿主处理解析、帮助、授权与执行。

```text
operit_editor({ args })
  → Tools.SoftwareSettings.exec(args)
  → execute_cli_command
  → PlatformCommandRegistry / PlatformCommandExecutor
  → PlatformCommandDispatcher
  → 原工具权限系统与 AIToolHandler
  → 对应业务服务
```

## 参数和返回值

脚本接收 `string[]` 或数组的 JSON 字符串，校验后逐项传递。宿主只接收 `args` 和已有系统调用上下文字段，不接受 `query`。JSON 正文中的 `__operit_` 参数一律拒绝；位置参数与正文的同名字段也不能互相覆盖。包工具本身的 `tool_name` 参数与位置上的目标选择器分开保存。

`[]`、`help`、命令组和 `help <group> <command>` 从同一个命令目录生成帮助。详细参数来自真实宿主声明；包、Skill 和 MCP 工具目录由现有 `CliToolModeSupport` 按启用状态及角色卡权限读取。

普通命令直接保留业务 `ToolResult` 的结构化数据、成功标记和错误。首位 `--json` 只序列化成功输出。原业务失败、权限拒绝和异常沿原工具链传播，不使用成功包装，也不自动重试目标。

## 权限和运行上下文

调度沿用现有代理的角色卡检查及 `executeProxyTargetWithPermissionCheck`，再进入实际 `AIToolHandler.executeTool`，保留权限通知和工具 hook。命令执行器不持有全局执行锁，不使用全局可变调用参数。

`ToolRuntimeContext` 增加可选 `callerName`，与既有角色卡和对话 ID 一起传入目标包。已经存在运行上下文时，以宿主上下文提供身份字段，JSON 正文不能覆盖。A2UI 发布和运行上下文仍由原 JS 原生调用桥显式传递与恢复。

不能用 `tool exec` 代理 `search`、`proxy`、`package_proxy` 或 `execute_cli_command` 本身。嵌套包工具仍可通过 SDK 发起命令，并继续接受目标授权检查。

## 旧能力迁移

| 原编辑器能力 | 新命令或实现 |
|---|---|
| 固定配置排查手册 | `[]` 与实际宿主详细帮助；使用说明迁入独立文档 |
| `get_performance_metrics` | `performance show [plugin_id]` |
| `how_make_skill` | `skill guide`，目录由真实 SkillManager 提供 |
| 沙盒包列表与启停 | `package list/enable/disable` |
| `debug_install_js_package` | `package install <source_path> [params-json]`，真实包解析、同名文件清理和安装校验 |
| `debug_install_toolpkg` | 同一安装命令，支持归档、源码目录及 manifest，复用已有 ToolPkg 调试安装服务 |
| `debug_run_sandbox_script` | `script run <params-json>`，复用软件层直接执行接口 |
| 环境变量读写 | `env get/set/unset` |
| MCP 重启和 `ping_mcp` | `mcp restart/ping`，包、Skill 及 MCP 加载共用 `package use` |
| TTS/STT 读取、修改、试听 | `speech show/set/test` |
| 模型配置增删改查和连接测试 | `model list/create/update/delete/test` |
| 功能模型绑定 | `model function-list/function-show/function-set` |
| 上下文总结读取与修改 | `context show/set <function_type>` |
| 角色卡管理及酒馆 JSON 导入导出 | `character list/show/create/update/delete/activate/clear/import/export` |

上下文总结修改先通过功能绑定定位配置，再仅提交显式请求的字段。调试安装去掉脚本内写死的正式版路径和广播目标；源码打包、同名处理、安装和刷新在宿主完成。安装的后续步骤失败时，结构化结果仍记录已完成安装及激活状态。

## 软件版本边界

用户已确认旧接口发布事实，并授权移除旧编辑器的工具名与 `query` 包装。所有原宿主注册和原 `Tools.SoftwareSettings` 方法保留，新增 `exec`、`execute_cli_command` 与调试安装工具。

新版编辑器必须随 **1.12.2+27** 软件发布。脚本检测缺少执行器时明确提示同步升级软件；不调用旧手册入口，不添加旧软件执行路径。旧客户端没有内置插件的软件版本限制能力，新元数据无法事后改变旧客户端行为。

本 PR 不承担整个软件的发行版本号变更，当前 dev 的版本号由正式发布流程切换到 27。发行说明应明确：“新版平台编辑器需要同步更新软件至 1.12.2+27 或更新版本，单独更新脚本无法补齐宿主接口。”

## 验证安排

仓库内加入命令解析、调度权限、调用上下文、命令执行及安装文件处理的 JUnit 回归；脚本与生产 SDK 桥接的 Node 回归通过 JUnit 包装接入现有 Android Tests 和 PR 检查。核心验证记录在 [验证与交付](02-verification.md)。使用示例见 [平台编辑器命令](../../../OPERIT_EDITOR_COMMANDS.md)。

完整 Android 构建和目标业务在新版软件上的实际安装、配置变更由 CI 和真机验收分别确认，核心单测不能替代这些结果。

[DONE]