---
owner: https://github.com/3316891527/Operit
branch: feat/operit-editor-commands
status: implemented
target-release: 1.12.2+27
---

# 内置平台编辑器命令入口

现有 operit_editor 的主入口返回固定排查手册，实际操作分散在大量工具元数据和包装函数中。改为单一结构化参数入口，让脚本调用宿主命令执行器，帮助信息和实际操作由同一命令目录维护。

## 已确定的兼容边界

用户已确认旧版正式发布，同时明确授权直接重构内置编辑器脚本，不保留旧脚本工具名和 query 参数的兼容包装。既有宿主工具注册和 Tools.SoftwareSettings 方法全部保留，新增 Tools.SoftwareSettings.exec(args) 与 execute_cli_command。

新版编辑器依赖软件层新增接口，随 1.12.2+27 上线。旧软件不能通过单独更新脚本获得执行器；脚本检测接口缺失时明确提示更新软件，不尝试调用旧手册或其他执行入口。旧客户端不能靠新增元数据获得事后版本限制，发行说明需要直接说明同步更新要求。

## 实现范围

1. [宿主命令与调用契约](01-command-contract.md)
2. 精简编辑器源码、生成 JavaScript 与内置资源同步
3. [针对性回归与验证记录](02-verification.md)
4. 提交独立 PR，发起同一提交的 Build 与 Android Tests，仅确认启动和 SHA

命令复用原有平台操作及其权限检查、角色卡工具访问规则和运行上下文。参数数组中的 JSON、空字符串、空格及换行保持完整，不拼接 shell 命令。未知命令和无效参数明确失败，执行失败不得包装为成功。

本轮不包含 cleanOnExit 清理策略调整，也不修改 Operit2 参考项目。

[DONE]