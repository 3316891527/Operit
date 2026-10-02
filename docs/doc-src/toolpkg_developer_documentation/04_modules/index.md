---
title: 模块 API 索引
status: draft
---

# 模块 API 索引

本节以 `examples/types/` 中实际公开的运行时命名空间为入口。以下页面从现有 API 参考迁入新文档树，属于逐方法补全的工作底稿；页面状态尚未达到最终覆盖审阅前，不代表已完成。

## 运行时基元与 ToolPkg

- [全局核心类型与调用约定](./core.md)：脚本工具调用、上下文函数和核心运行时类型。
- [工具参数与调用类型](./tool-types.md)：工具名、参数映射与类型化返回。
- [ToolPkg 注册对象](./toolpkg.md)：UI、生命周期、聊天、Prompt、摘要、AI Provider、IPC、WASM 和资源注册。
- [公共结果类型](./results.md)：宿主工具结果及字段结构。

## 聊天与记忆

- [Chat](./chat.md)：聊天记录、消息、模型调用及上下文相关接口。
- [Memory](./memory.md)：记忆搜索、读写、关系和结果结构。

## 文件与网络

- [Files](./files.md)：文件读写、目录、搜索、编辑与转换接口。
- [Net](./network.md)：HTTP、网页访问、搜索和网络结果类型。
- [OkHttp](./okhttp.md)：OkHttp Java Bridge 的请求构造和响应处理。

## 系统与设置

- [System](./system.md)：终端、设备、应用、自动化和系统操作接口。
- [SoftwareSettings](./software_settings.md)：模型、语音、包、环境变量和应用设置管理接口。

## 自动化与编排

- [Workflow](./workflow.md)：工作流查询、创建、执行和结果数据。
- [Tasker](./tasker.md)：Tasker Runtime API。
- [Android](./android.md)：Intent、PackageManager、ContentProvider、SystemManager、DeviceController 和 AdbExecutor。

## UI 与工具库

- [UI](./ui.md)：页面结构、节点定位和 UI 自动化操作。
- [FFmpeg](./ffmpeg.md)：媒体转码、编码器和转码参数。
- [CryptoJS](./cryptojs.md)：加密、摘要与编码库接口。
- [Jimp](./jimp.md)：图像处理库接口。

## 逐方法审计进度

页面已按声明和运行时整理，但尚未完成全仓逐符号核对。当前仍需完成：

- `core.d.ts` 的 NativeInterface 全方法契约与每条工具调用结果的分支核验。
- `results.d.ts` 全部公开结果类型的字段映射。
- 每个 `04_modules/*.md` 页面与对应 facade/runtime 的逐方法对照，优先审查 Android、Files、Net、OkHttp、System、SoftwareSettings、Memory、Workflow、Tasker、UI、Chat 和 ToolPkg。
- Compose 生成 props 的每个字段与宿主 renderer 分支对照；WebView、Canvas 和组件默认值的宿主语义审查。
- 全局 `Tools`、辅助对象与 exports 注册入口的调用时序和错误路径。

新建页面只解决入口缺失；以上条目完成并通过链接/版本审查前，目录状态继续保持 draft。