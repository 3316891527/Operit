package com.ai.assistance.operit.core.config.links

/** 文件引用属于输出格式，在各种工具模式及未绑定工作区时都提供给模型。 */
internal object WorkspaceFileLinkGuidelines {
    fun forLanguage(useEnglish: Boolean): String = if (useEnglish) ENGLISH else CHINESE

    private val CHINESE = """
本地文件引用：
- 引用文件或目录时使用标准 Markdown 链接 [显示名称](目标)，目标使用 file:///绝对路径，并用 ?environment=android、?environment=linux 或 ?environment=repo%3A仓库名称 保留真实来源。
- 按路径段进行 URL 编码并保留路径分隔符 /；空格写为 %20，括号写为 %28 和 %29，文件名中的 #、?、% 分别写为 %23、%3F、%25；查询参数值也按 URL 编码。
- 引用文本行号时在目标最后追加 #L12，一基行号表示第 12 行；不需要行号时省略。应用打开文件时会定位该行，使用与 GitHub 文件链接相同的行号约定。
- 链接必须来自用户提供或工具确认的真实路径、环境和行号；下面只是写法示例，输出时替换为实际文件。将可点击链接放在普通正文中。
- 普通网页继续使用原 HTTP/HTTPS 地址。
示例：
[报告](file:///sdcard/Download/report.md?environment=android)
[源码第12行](file:///home/user/project/main.kt?environment=linux#L12)
[仓库源码第12行](file:///src/main.kt?environment=repo%3Ademo#L12)
[特殊字符文件](file:///sdcard/Download/notes%20%281%29%23%3F%25.md?environment=android)
""".trimIndent()

    private val ENGLISH = """
Local file references:
- Use standard Markdown links [label](target) for files and directories. Use file:///absolute/path targets and preserve their actual source with ?environment=android, ?environment=linux, or ?environment=repo%3ArepositoryName.
- URL-encode each path segment while preserving / separators. Encode spaces as %20, parentheses as %28 and %29, and filename characters #, ?, and % as %23, %3F, and %25. URL-encode query parameter values too.
- For a text location, append #L12 to the end of the target. Line numbers are 1-based; omit the fragment when no line is needed. The app opens the file at that line using the same line-anchor convention as GitHub file links.
- Use only real paths, environments, and line numbers supplied by the user or confirmed by tools. The examples below illustrate syntax; replace them with the actual file. Put clickable links in normal prose.
- Keep original HTTP/HTTPS addresses for ordinary web links.
Examples:
[Report](file:///sdcard/Download/report.md?environment=android)
[Source line 12](file:///home/user/project/main.kt?environment=linux#L12)
[Repository source line 12](file:///src/main.kt?environment=repo%3Ademo#L12)
[File with special characters](file:///sdcard/Download/notes%20%281%29%23%3F%25.md?environment=android)
""".trimIndent()
}
