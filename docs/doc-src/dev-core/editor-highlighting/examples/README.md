# 自定义高亮示例

将需要的 JSON 文件复制到应用私有目录 `files/config/editor/highlighting/`。目录在首次打开编辑器时创建；这些示例不会自动覆盖用户配置。

- `_global.json` 使用自由命名的“运算符”样式为现有运算符 scope 换色
- `markdown.json` 调整 Markdown 标题、引用、链接和删除线，继续使用内置 Markdown 语法
- `demolang.json` 为 `.operitdemo` 和 `OperitDemo` 提供完整自定义规则，演示注释、字符串、插值、递归表达式、捕获组和嵌入 JavaScript
- `sample.operitdemo` 是 `demolang.json` 对应的编辑示例

保存配置后，已打开的编辑器自动重新分析；Diff 在打开或刷新时重新读取配置。完整字段、匹配顺序和错误处理见[配置说明](../editor-highlighting.md)。


新建的空 JSON 可以直接留在配置目录中继续编辑，不会触发配置错误。填写内容并保存后才会校验和生效。

将 `demolang.json` 保存到配置目录后，在应用内点击 `sample.operitdemo` 即可默认进入文本编辑器。文件名关联也适用；内容必须是文本，既有媒体、文档和归档打开方式不受影响。配置有内容但写错时仍会明确报告错误，修正保存后重新打开示例即可。
