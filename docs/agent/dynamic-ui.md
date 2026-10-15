# 动态界面工具

加载 `dynamic_ui` 后先调用 `read_guide`，按需要用原生展示块或交互块补充正常回答。模型用受限 Express 描述界面，`prepare_card` 返回短引用；将 `a2ui-ref` 围栏放在正文相应位置，宿主在同一请求中展开为自包含 A2UI v0.9 卡片。不要手写或重复抄写卡片 JSON。

适合步骤、流程、数据对比、表格、多项指标，以及用户需要输入、选择或本地试算的内容。短事实、简短回答和普通代码继续使用 Markdown。纯展示内容无需按钮、表单或未使用的数据。

`read_example` 默认编译展示示例；设置 `source_only=true` 只学习源码。购物清单、出行费用和筛选图表示例使用演示数据，不应当作查到的事实。修改后使用 `prepare_card` 编译。组件参数和当前合同由 `read_guide` 及原生目录生成，编译失败按返回位置修正。

控件与本地计算在设备内处理。AI 提交及工具请求经确认后进入普通对话管线，仍受当前聊天、角色卡和权限约束。不要添加无实际用途的提交按钮。流式未完成的卡片不可交互，临时引用不能跨请求复用。

`live=true` 只允许本轮修改初始化的 `/agent` 数据，`patch_card_data` 必须使用最近版本号；用户输入与 Agent 数据分开。`update_card` 返回独立新修订，不修改旧卡片的用户状态。

图标通过 `list_icons` 按名称查询；卡片支持固定版本的 Lucide 数据。当前接入不提供段落内的徽章或图标 Markdown 扩展。

仅当用户明确要求 HTML/CSS 或高级视觉效果时使用 `prepare_html`。这类块在用户点击后加载，不能执行作者脚本、联网或直接调用工具；需要持久输入、权限操作或 AI 提交时使用原生组件。

宿主实现位于 `app/src/main/java/com/ai/assistance/operit/ui/common/dynamicui/`；AndroidX 兼容层位于 `a2ui-compat/`，来源和许可证随源码保留。当前实现与验证边界见 [开发记录](../TODO/distribution-a2ui/index.md)。
