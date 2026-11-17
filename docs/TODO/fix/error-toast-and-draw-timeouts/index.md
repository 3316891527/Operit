---
repo: https://github.com/3316891527/Operit
status: implemented
---

# 错误提示与生图上传超时审计

本次检查了 Android 源码中 211 个含对话框调用的文件，按错误状态及按钮行为复核候选。截图同款 `ErrorDialog` 有 2 个调用点，连同其他自动错误提示，共处理 7 个展示入口。

## 错误提示

| 入口 | 原行为 | 修改后 |
| --- | --- | --- |
| 包管理导入 | 请求失败弹窗，复制或确认 | 普通 `OKToast.error`，排队后消费导入错误状态 |
| 聊天页面 | 请求失败弹窗，复制或确认 | 普通 `OKToast.error`，消费错误并保留原输入状态清理 |
| 工作台终端 | 终端启动失败弹窗 | 普通 `OKToast.error`，消费会话错误状态 |
| 聊天历史文件夹 | 操作失败弹窗 | 普通 `OKToast.error`，消费操作错误状态 |
| ToolPkg Compose DSL 对话框宿主 | 界面加载失败后显示关闭弹窗 | 普通 `OKToast.error`，关闭失败的界面宿主 |
| 应用更新检查 | 检查失败进入更新结果弹窗 | 普通 `OKToast.error`，关闭本次结果状态 |
| 市场更新检查 | 检查失败进入结果弹窗 | 普通 `OKToast.error`，供市场更新检查入口共用 |

Compose 入口通过 `ErrorToast` 的 `LaunchedEffect` 发出一次提示并消费源状态，避免重组或重新进入页面时重播错误。提示使用 `OKToast.LENGTH_LONG`，不使用 `requestError`。原 `ErrorDialog` 实现和引用已删除。

市场检查中无需后续选择的结果也使用普通提示：已是最新版使用 success、非市场来源使用 info、条目不存在使用 warning。有可用更新时保留“前往更新”确认。

删除确认、编辑输入、安装或下载进度、重试操作，以及用户主动查看的加载错误详情继续使用各自的交互界面。

## 生图脚本

宿主 `StandardHttpTools.resolveHttpTimeouts` 未收到参数时，读取超时为 20 秒、写入超时为 15 秒。`Tools.Net.uploadFile` 单独创建宿主 HTTP 客户端，不继承脚本的 OkHttp 配置。

| 脚本 | 发送路径 | 审计结果 |
| --- | --- | --- |
| OpenAI Draw | 自建客户端与宿主 multipart | 已于此前显式传入 600000 毫秒，本次保持 |
| MiniMax Draw | 自建客户端与 BeeIMG multipart 上传 | 本次为参考图上传补上 `timeout_ms: HTTP_TIMEOUT_MS` |
| Nanobanana Draw | 自建客户端与 BeeIMG multipart 上传 | 本次为参考图上传补上 `timeout_ms: HTTP_TIMEOUT_MS` |
| Qwen Draw | 自建 600 秒客户端 | 未发现宿主 20 秒上传限制 |
| SiliconFlow Draw | 自建 600 秒客户端 | 未发现宿主 20 秒上传限制 |
| SpaceXAI Draw | 自建 600 秒客户端 | 未发现宿主 20 秒上传限制 |
| Zhipu Draw | 自建 600 秒客户端 | 未发现宿主 20 秒上传限制 |

独立 BeeIMG 图片上传脚本使用同一宿主 multipart 路径，也补上 600000 毫秒。三套改动均同步 `.ts` 和分发用 `.js`，不改变 API 地址、表单字段和响应解析。

## 验证

- `git diff --check` 通过
- 三份修改后的 JS 均通过 `node --check`，未执行实际绘图或上传请求
- 全应用源码检索没有剩余 `ErrorDialog` 及 `dismissErrorDialog` 引用
- Android 编译和 JVM 检查交由 PR 的 GitHub Actions 执行；本地未运行 Gradle

[DONE] 源码修改与审计完成，远端检查和合并状态见对应 PR。
