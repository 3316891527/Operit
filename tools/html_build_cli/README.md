# build html 原生终端命令

本 Rust 包生成 `operit-html-build` 可执行文件，安装后使用 `build` 命令，负责命令解析、Ubuntu 项目扫描、流式 IPC、实时日志和成品发布。Android Kotlin 服务负责模板检查、APK 打包签名和 Windows 运行目录生成。用户用法见 [build html 命令](../../docs/doc-src/feature-protocol/html_build_cli.md)。

## 构建与分发

工具链由 `tools/terminal_commands/rust-toolchain.toml` 固定，与 `apply_patch`、`web-chat` 共用 ARM64 GNU 环境。从仓库根目录执行：

```bash
export CARGO_TARGET_AARCH64_UNKNOWN_LINUX_GNU_LINKER=aarch64-linux-gnu-gcc
cargo +1.95.0 build --manifest-path tools/html_build_cli/Cargo.toml --bin operit-html-build \
  --release --target aarch64-unknown-linux-gnu --locked
python3 tools/html_build_cli/package_assets.py \
  --binary tools/html_build_cli/target/aarch64-unknown-linux-gnu/release/operit-html-build \
  --output app/build/generated/terminal-command-assets
```

Cargo 禁止将二进制目标命名为 `build`，因此编译产物使用 `operit-html-build`。打包器将它写入固定资源路径 `aarch64/build`，安装器继续提供 `/usr/local/bin/build`。

交叉编译需要 `gcc-aarch64-linux-gnu` 和 `libc6-dev-arm64-cross`。资源根目录为 `html_build_command`，包含 `aarch64/build`、`install.sh` 和 SHA-256 清单。当前打包器只处理自己的资源，注册与汇总校验见[公共终端命令层](../terminal_commands/README.md)。Gradle、Build、Android Tests 和 PR Check 均使用共享资源校验与构建步骤。

Android 安装链在终端初始化时启动 `HtmlBuildCommandServer`，验证资源后执行安装脚本。安装器在 `/usr/local/bin` 写入 `build` 包装命令和按哈希命名的实际二进制，用 `flock` 串行安装，并原子更新包装命令。包装命令固定 Android 宿主 UID 对应的 `OPERIT_BUILD_SOCKET`，不会使用 proot 虚拟 root UID 推算通道。

## 流协议

一次构建对应一个 Linux 抽象 Unix socket 连接，名称为 `operit.build.<Android UID>`。Android 通过 peer credentials 只接受宿主 UID 或真实 root。协议版本为 `1`，JSON 控制帧以换行结束，最大 `64 KiB`；二进制文件依据帧中声明的精确长度读取，不经过 Base64。

客户端先发送 camelCase 元数据，随后逐个发送 `path`、`size` 文件帧及原始内容，再发送可选图标内容，最后关闭连接的写半边。元数据包含目标、应用信息、入口、文件数量、总字节数及图标字节数。Android 在私有缓存中建立独立任务目录，严格检查相对路径、重复路径、文件数和字节数。

服务端可随时发送 `progress`，包含 `progress` 比例及 `message`。客户端单独的读取线程在上传期间就接收并刷新日志。服务端每两秒报告当前实际阶段和累计耗时，不按时间伪造完成比例。任务排队与现有打包引擎的长耗时阶段均保留进度输出。

构建完成后发送 `directory`、`artifact` 帧，文件帧后紧随精确 `size` 字节；最后发送 `result`，包含 `success`、`message` 和入口成品 `outputs`。Windows 发送整个运行目录。客户端验证单根应用名、完整清单和传输限额后，在同一文件系统使用 Linux `renameat2(RENAME_NOREPLACE)` 发布，避免并发覆盖已有文件或目录。失败时返回非零状态并清理接收缓存。

## Android 实现

源码位于 `app/src/main/java/com/ai/assistance/operit/core/htmlbuild/`：

```text
htmlbuild/
	HtmlBuildProtocol.kt
	HtmlBuildCommandServer.kt
	HtmlAppBuilder.kt
```

协议层校验请求；socket 服务接收项目、输出进度和发送成品；构建适配层通过 Mutex 串行使用现有 `ApkEditor`、`KeyStoreHelper` 和 `ExeEditor`。模板仅使用应用已安装的离线导出资源，缺失时明确失败。每个任务结束后清理缓存。自定义入口通过构建副本的根跳转页实现，不改写用户项目。

## 回归

Rust 回归源码覆盖参数、路径和控制帧；Kotlin JVM 回归覆盖序列化字段、目标、版本、应用名和传输限制。`tests/check_distribution.py` 用 QEMU 执行实际 ARM64 二进制，覆盖完成前可见的实时日志、APK 二进制完整性、Windows 完整目录、输出目录排除、失败清理、成品竞争、安装器校验与资源清单。执行入口已接入共享 terminal-commands action。

这些用例使用模拟构建服务验证命令与分发；真实 Android 模板、签名与 Windows 成品运行仍需完整构建和设备验收。
