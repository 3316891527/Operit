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
| `theme` | `targets`、`fields`、`show`、`set`，按需查询并修改外观 |
| `performance` | `show`，可指定插件 ID |
| `tool` | `list`、`show`、`exec`，发现和执行已有宿主或包工具 |

`context set` 只更新显式提供的上下文总结字段，不额外写入一整组默认参数。模型配置可能同时绑定多个功能，修改前应先读取绑定关系。

```json
{"args": ["context", "show", "CHAT"]}
{"args": ["context", "set", "CHAT", "{\"enable_summary\":true,\"summary_message_count_threshold\":16}"]}
```

## 主题外观

本次新增命令需要包含主题控制实现的宿主版本。已有 1.12.2+27 命令入口不代表包含这些新操作，以空数组返回的实时帮助为准；单独更新编辑器脚本不会添加宿主能力。

新增范围为主题与配色、字体与头像、背景、消息外观和消息信息显示。字段分为 colors、fonts、avatars、background、messages、info；未开放语言、全局布局、显示行为、输入框、侧边栏、主题工作室或玻璃细节参数。

先发现目标和需要的字段：

```json
{"args":["theme","targets"]}
{"args":["theme","fields","fonts"]}
{"args":["help","theme","set"]}
{"args":["theme","show","{\"section\":\"info\"}"]}
```

默认 current 使用调用对话的主题，群聊使用角色组主题。没有对话身份的直接调用使用应用当前激活主题。显式 card 或 group 目标必须带 theme targets 返回的精确 target_id；user 目标只接受全局用户头像和名称。命令不会为了修改非当前角色而切换激活角色。

```json
{"args":["theme","show","{\"target_type\":\"card\",\"target_id\":\"角色ID\"}"]}
{"args":["theme","show","{\"target_type\":\"user\"}"]}
```

theme show 返回 revision。修改时把同一目标的 revision 原样放入 expected_revision，下面的“上次读取的revision”需替换为实际返回的 64 位十六进制值。

```json
{"args":["theme","set","{\"expected_revision\":\"上次读取的revision\",\"changes\":{\"font_scale\":1.1,\"show_model_name\":false,\"show_message_token_speed\":true}}"]}
{"args":["theme","set","{\"target_type\":\"card\",\"target_id\":\"角色ID\",\"expected_revision\":\"上次读取的revision\",\"changes\":{\"custom_primary_color\":\"#FF315A9F\",\"bubble_ai_text_color\":\"#FFE8EEF8\"}}"]}
```

changes 只更新提供的字段，其他偏好保留。字段帮助声明类型、范围、单位、枚举、可空与作用范围；布尔值和数值不接受字符串代替，颜色接受 #RRGGBB 或 #AARRGGBB，opacity 是 0 到 1 的不透明度，值越小越透明。媒体背景需要同时启用 use_background_image；手动 theme_mode 需要关闭 use_system_theme 才主导模式。

设置主色或辅助色会启用自定义配色，文件字体会启用对应字体，消息玻璃模式与图片模式遵循界面互斥规则。明确互相矛盾的参数会整体拒绝，不会静默忽略。message 图片裁剪和拉伸仍使用已有字段，九宫格自动解析或工作室工程编辑没有加入命令。

资源接受可读 Android 绝对路径、file URI 或已授权的 content URI。图片、视频、字体经内容和大小校验后复制到应用私有受管理目录；成功后即使原附件被清理也能保留，失败只清理本次导入副本。命令不下载网络资源，字体保存为路径，图片与视频保存为文件 URI。null 仅在字段帮助允许时用于清除；例如清除角色用户头像后恢复全局头像来源。

```json
{"args":["theme","set","{\"expected_revision\":\"上次读取的revision\",\"changes\":{\"custom_ai_avatar_uri\":\"/sdcard/Download/avatar.png\"}}"]}
{"args":["theme","set","{\"expected_revision\":\"上次读取的revision\",\"changes\":{\"background_image_uri\":\"/sdcard/Download/background.jpg\",\"background_media_type\":\"image\",\"use_background_image\":true,\"background_image_opacity\":0.35,\"use_background_blur\":true,\"background_blur_radius\":12}}"]}
```

保存结果包含实际目标、新 revision、saved、verified、updatedFields 和读回偏好。valueSource 为 preferences；若 hasThemeScript=true，现有自定义主题脚本可能覆盖最终外观，这些数值不代表最终渲染结果。偏好修改不重建 Activity，尚未激活的角色在显示该主题时使用新设置；verified 仅代表持久值读回一致。
读取后若有其他修改，保存会拒绝旧 revision，需要重新读取。主题设置页后续保存旧草稿时会三方合并未冲突字段；同字段不同修改则保留草稿并报告保存失败，避免覆盖命令的新设置。保存期间继续编辑的内容仍保留为未保存草稿。全局名称独立合并，更新名称会保留当前头像。

主题页的重置将默认值作为草稿合并保存，保留角色头像与标题，不再通过清除全部 override 完成；未被草稿修改的并发更新仍会保留。“应用到所有角色”先检查并保存当前角色，再沿用原有的全角色视觉覆盖行为。若后续传播、名称或布局保存失败，已完成的主题保存仍保留，界面基线会反映实际已保存部分。

如果持久化成功后读回失败，命令返回失败状态并携带 saved=true、verified=false，调用方应重新读取确认，不能把失败状态理解为所有设置均未修改。

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
./gradlew :app:testDebugUnitTest --tests '*PlatformCommand*Test' --tests '*PlatformPackageFilesTest' --tests '*OperitEditorRuntimeTest' --tests '*ThemeCommand*Test' --tests '*ThemeDraft*Test' --tests '*ThemeEditorSessionMergeTest'
```

运行时回归通过 JVM 测试入口接入 Android Tests 和 PR 检查，验证脚本、实际 SDK 桥接、错误传播、参数完整性、嵌套与并发、命令解析、权限调度及安装文件处理。测试中的目标业务调用使用受控替身；真机上的配置修改与包安装需使用新版软件验收。
