---
repository: https://github.com/3316891527/Operit
branch: fix/waifu-thinking-preview-length
status: implementation-complete-awaiting-ci
---

# Waifu 折叠思考预览长度

原预览使用 UTF-16 长度约 240 的限制，折叠标题容易占满一行。根据用户确认，将正文预览限制为八个 Unicode code point，超出时追加一个 `…`。

## 范围与实现

仅修改 `WaifuThinkingPreview.kt` 与已有 `WaifuThinkingPreviewTest.kt`。调用点仍只在 Waifu 过程模式且思考折叠时生成预览；普通模式和展开全文保持原有渲染。

继续通过 `ThinkingMarkup.blockAt` 取得真实正文边界，未闭合思考也可预览。按 code point 复制，避免截断 UTF-16 代理对。连续空白归一为一个空格，正文前后空白不展示，内部归一化空格计入八字符上限。达到上限后继续确认是否还有非空白正文，因此恰好八字加尾部空白不会误加省略号。

## 验证与限制

更新原有四项回归，并新增六项边界回归源码，覆盖八字尾部空白、第九字、空白计数、emoji 边界、空或短正文以及 token 闭合后的普通正文。计数按 Unicode code point，组合字符与多 code point emoji 会分别计数。

本地仅做严格 UTF-8、Kotlin tree-sitter 语法解析和 `git diff --check`。未运行 Gradle、JVM 测试、设备测试或实机视觉验证。Debug 构建和 Android Tests 由远端工作流执行；启动登记不等于构建或测试通过。

[DONE] 实现与回归源码已完成，待远端验证及实机确认。
