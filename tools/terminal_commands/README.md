# 公共终端命令构建与校验

本目录保存应用内终端命令共用的 Rust 工具链声明与资源汇总校验。CI 构建步骤位于 [terminal-commands action](../../.github/actions/terminal-commands/action.yml)。

## 职责划分

- `tools/terminal_commands/`：固定公共编译工具链，调用全部组件的资源或 APK 校验器。
- [native_apply_patch](../native_apply_patch/README.md)：补丁引擎、CLI、安装器、许可文件、独立打包及校验。
- [web_chat_cli](../web_chat_cli/README.md)：HTTP 服务管理命令、安装器、独立打包及校验。
- [html_build_cli](../html_build_cli/README.md)：HTML 项目构建命令、实时进度协议、安装器、独立打包及校验。

各组件的 `package_assets.py` 都只处理自身资源。新增命令在自己的目录维护打包规则，再注册到公共校验入口及共享构建步骤；无需修改其他命令的打包脚本。

## 汇总校验

三个命令均准备好资源后，从仓库根目录执行：

```bash
python3 tools/terminal_commands/verify_assets.py \
  --verify --output app/build/generated/terminal-command-assets
python3 tools/terminal_commands/verify_assets.py \
  --verify-apks app/build/outputs/apk \
  --output app/build/generated/terminal-command-assets
```

Gradle 的 `verifyTerminalCommandAssets` 调用公共资源校验入口，Build 和 PR 工作流调用公共 APK 校验入口。每个组件继续验证自己的 ELF 架构、SHA-256、安装脚本、清单及许可要求；任何组件缺失、损坏或 APK 与生成目录不一致都会使汇总校验失败。

Build、Android Tests 与 PR 检查都使用共享 action。共享目录、命令源码和 action 的变更均包含在相应工作流与 PR 检查范围内。

## 命令帮助语言

`apply_patch --help`、`web-chat --help`、`build --help` 和 `build html --help` 使用本目录 `help/zh/` 与 `help/en/` 的独立资源，编译时嵌入各命令。中文界面选择中文，其他界面统一英文。

应用以原子写入维护 `files/terminal-commands/language`，会话初始化导出 `OPERIT_COMMAND_LANGUAGE_FILE`。每次显示帮助都读取此文件，因此已有会话也能读取新的应用语言。指定文件不可读时命令明确返回错误。独立运行且未指定语言文件时，可用 `OPERIT_COMMAND_LANGUAGE=zh` 或 `en` 选择帮助语言；未设置时使用英文。

更新帮助或共享语言模块后，需要重新生成三个命令的资源，现有共享 CI action 会执行这一过程。语言选择不依赖固定为英文的终端 `LANG`。
