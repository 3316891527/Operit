# 原生 A2UI

A2UI 的生成、校验、修订、数据更新和 Compose 渲染由 Kotlin 提供，不需要导入或激活任何脚本包。

原生工具包括 `a2ui_catalog`、`a2ui_icons`、`a2ui_prepare`、`a2ui_revise`、`a2ui_patch_data`，沿用内置工具的权限、角色卡可见性和真实请求上下文。基础工具定义只描述接口，不要求模型优先生成卡片或每次先读取引导。

`a2ui_catalog` 返回当前 Express 组件、函数和参数定义。`a2ui_prepare(source, live=false)` 将 Express 编译为原生 A2UI v0.9 文档并返回本轮短引用；将引用放在回答正文的 `a2ui-ref` 围栏内，原生消息管线会展开为自包含 `a2ui` 卡片。直接输出符合当前协议和组件目录的完整 `a2ui` 围栏也可被原生渲染器识别。

示例源码 `root=Column(children=[Heading(text="标题"),Text(text="内容")])` 可直接交给 `a2ui_prepare`。修订使用 `a2ui_revise`，生成新的引用并保留旧卡片；实时数据通过 `a2ui_patch_data` 按当前版本更新。引用不能跨请求使用，已结束请求不能继续生成或更新卡片。

可选的 `examples/a2ui_guide.js` 仅提供 `read_guide` 和 `read_example`，只返回说明与 Express 示例源码，不调用原生接口、不生成卡片、不随 APK 内置。需要详细说明时可手动导入或读取该文件；不使用引导包不会关闭原生能力。

网络中的 A2UI 示例可能采用不同协议版本、组件目录和交互合同，需要对照本应用的原生目录。示例数据不能当作已查询到的事实。

宿主包为 `ui.common.a2ui`，工具入口位于独立的 `core.tools.defaultTool.a2ui`。普通卡片状态与 live 最终快照保存在应用私有目录 `files/a2ui/` 下。本功能尚未正式发布，不迁移旧调试目录中的填写状态。

live 卡片必须在源码中初始化 `$/agent` 对象，并将可更新的数据绑定到该对象下。`a2ui_patch_data` 的 data 是整个 Agent 对象的替换值；例如 `{"progress":50}`，不再包裹 agent 键。用户输入应使用其他路径。

```text
$/agent={progress:10}
root=Progress(label="实时进度",number=divide(a=$/agent/progress,b=100))
```

上例交给 `a2ui_prepare(source, live=true)` 后，按返回引用更新数据，expected_version 从 0 开始，每次使用上一次返回的版本。Progress.number 的范围是 0～1，示例将 0～100 的数值除以 100。`a2ui_prepare` 缺少或错误初始化 Agent 对象时返回 `invalid_card`；真实生成轮次失效时返回 `inactive_turn`。
