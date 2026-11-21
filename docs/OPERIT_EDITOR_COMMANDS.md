# Operit 平台编辑器命令

新版内置 `operit_editor` 只公开一个工具，使用 `args` 字符串数组请求宿主执行实际操作。需要将 Operit 软件同步更新到 **1.12.2+27 或更新版本**；单独更新脚本不能为旧软件添加命令执行器。

旧版客户端没有可事后补上的脚本最低软件版本限制。新版脚本发现宿主缺少 `Tools.SoftwareSettings.exec` 时会直接提示更新软件。旧编辑器的 `query` 参数和多工具包装已移除，已有宿主工具及 `Tools.SoftwareSettings` 其他方法继续保留。

## 调用方式

```json
{"args": []}
```

空数组返回当前宿主命令帮助。查看某一项操作的参数：

```json
{"args": ["help", "model", "update"]}
```

读取实际状态，再按用户授权修改，最后重新读取核验：

```json
{"args": ["package", "list"]}
{"args": ["package", "enable", "sample"]}
{"args": ["package", "list"]}
```

数组中的每个元素都是一个完整参数，不再拼接或按空白拆分。JSON 正文作为单个参数传入，字符串里的空格、空串和换行会保留。也接受上述数组的 JSON 字符串，不接受自然语言 `query`。

```json
{"args": ["model", "update", "config-id", "{\"temperature\":0.6,\"temperature_enabled\":true}"]}
{"args": ["env", "set", "EXAMPLE", "包含空格的值\n第二行"]}
{"args": ["package", "exec", "sample:run", "{\"message\":\"你好\"}"]}
```

首位 `--json` 将成功结果序列化为 JSON 文本；默认保留宿主业务数据的结构。执行失败始终保留失败状态和错误，不因输出格式变成成功。只在参数首位解释 `--json`，值位置里的同名文字继续按原值处理。

```json
{"args": ["--json", "tool", "list", "model"]}
```

## 命令范围

完整帮助和具体参数以运行中的宿主为准。帮助与解析使用同一命令目录，参数说明复用宿主工具声明；`tool list` 和 `tool show` 还会读取当前可访问的包、Skill 和 MCP 工具。

| 命令组 | 操作 |
|---|---|
| `package` | `list`、`enable`、`disable`、`use`、`install`、`exec` |
| `script` | `run`，直接调试文件或内联沙盒脚本 |
| `skill` | `guide`、`show` |
| `env` | `get`、`set`、`unset` |
| `permission` | `show`、`set` |
| `mcp` | `restart`、`ping` |
| `speech` | `show`、`set`、`test` |
| `model` | `list`、`create`、`update`、`delete`、`test`、`function-list`、`function-show`、`function-set` |
| `context` | `show`、`set`，按功能绑定定位模型配置 |
| `character` | `list`、`show`、`create`、`update`、`delete`、`activate`、`clear`、`import`、`export` |
| `performance` | `show`，可指定插件 ID |
| `tool` | `list`、`show`、`exec`，发现和执行已有宿主或包工具 |

`context set` 只更新显式提供的上下文总结字段，不额外写入一整组默认参数。模型配置可能同时绑定多个功能，修改前应先读取绑定关系。

```json
{"args": ["context", "show", "CHAT"]}
{"args": ["context", "set", "CHAT", "{\"enable_summary\":true,\"summary_message_count_threshold\":16}"]}
```

## 调试安装

`package install` 支持可读取的 Android 绝对路径：`.js`、`.toolpkg`、包含根 manifest 的 ToolPkg 源码目录，以及 `manifest.json` / `manifest.hjson` 本身。软件根据自身安装包确定外部包目录，不再在脚本中写死正式版的目录和广播组件。

```json
{"args": ["package", "install", "/sdcard/Download/sample.js"]}
{"args": ["package", "install", "/sdcard/Download/sample.toolpkg", "{\"reset_subpackage_states\":true,\"activate_subpackages\":[\"sample_tools\"]}"]}
{"args": ["script", "run", "{\"source_path\":\"/sdcard/Download/example.js\",\"params_json\":{\"name\":\"测试\"},\"wait_ms\":15000}"]}
```

安装会替换同名开发包。JS 包默认启用并激活；ToolPkg 容器默认启用，使用 `activate_subpackages` 指定要启用和激活的子包 ID。`enable_after_install` 和 `activate_after_install` 可显式设为 `false`，ToolPkg 容器本身不能激活。请求激活时，相应包会同时启用。

源码目录在应用缓存内打包，跳过 `.git`、`__pycache__`、`.DS_Store` 和 `Thumbs.db`，拒绝符号链接。安装完成后核对外部包来源与脚本内容，后续启用或激活失败会返回失败，同时在结构化结果中保留已经安装的状态和步骤日志。

## 权限与结果

命令执行复用角色卡工具访问限制、目标工具权限检查和工具 hook。嵌套调用继续使用发起者的角色卡、对话与调用者身份；JSON 正文不能伪造 `__operit_` 系统参数。目标工具只执行一次，错误和诊断数据沿原工具链返回。

`tool exec` 可以执行明确指定的现有工具；`search`、`proxy`、`package_proxy` 和命令执行器本身不能作为代理目标。需要调用其他包时使用 `package exec`，该包的调用仍可继续通过 SDK 发起受控命令。

## 开发验证

编辑 `plugins/examples/operit_editor.ts` 后，按示例包的 TypeScript 配置生成 JavaScript，并同步 `plugins/examples/operit_editor.js` 与 `app/src/main/assets/packages/operit_editor.js`。完整同步脚本见 [脚本开发指南](SCRIPT_DEV_GUIDE.md)。

```bash
node --test tools/operit-editor/tests/command-entry.test.cjs
./gradlew :app:testDebugUnitTest --tests '*PlatformCommand*Test' --tests '*PlatformPackageFilesTest' --tests '*OperitEditorRuntimeTest'
```

运行时回归通过 JVM 测试入口接入 Android Tests 和 PR 检查，验证脚本、实际 SDK 桥接、错误传播、参数完整性、嵌套与并发、命令解析、权限调度及安装文件处理。测试中的目标业务调用使用受控替身；真机上的配置修改与包安装需使用新版软件验收。
