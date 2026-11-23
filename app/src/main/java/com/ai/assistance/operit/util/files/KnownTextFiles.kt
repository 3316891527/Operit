package com.ai.assistance.operit.util.files

import java.util.Locale

/** 为打开和分享登记确定的文本格式，不依赖设备的 MIME 扩展名表或语法高亮目录。 */
internal object KnownTextFiles {
    private val plainTextExtensions = setOf(
        "txt", "text", "log", "md", "markdown", "rst", "adoc", "asciidoc",
        "ini", "conf", "config", "cfg", "cnf", "properties", "env",
        "gitignore", "gitattributes", "editorconfig", "srt", "vtt",
        "java", "kt", "kts", "gradle", "c", "cc", "cpp", "cxx", "h", "hh", "hpp", "hxx", "cs",
        "py", "rb", "php", "go", "swift", "rs", "dart", "lua", "sql", "jsx", "tsx",
        "sh", "bash", "zsh", "fish", "bat", "cmd", "ps1",
    )
    private val plainTextNames = setOf(
        "readme", "license", "licence", "notice", "changelog", "authors", "contributors", "copying",
        "install", "news", "todo", "version", "makefile", "dockerfile", "containerfile",
        "gemfile", "rakefile", "vagrantfile", "buildfile", "jenkinsfile", "gradlew",
        ".gitignore", ".gitattributes", ".editorconfig", ".env", ".npmrc", ".yarnrc",
    )

    fun mimeType(fileName: String): String? {
        val name = fileName.substringAfterLast('/').lowercase(Locale.ROOT)
        if (name in plainTextNames) return "text/plain"
        val extension = name.substringAfterLast('.', "")
        if (extension in plainTextExtensions) return "text/plain"
        return when (extension) {
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js", "mjs", "cjs" -> "application/javascript"
            "json" -> "application/json"
            "jsonc", "json5", "jsonl", "ndjson" -> "text/plain"
            "xml" -> "application/xml"
            "yaml", "yml" -> "application/yaml"
            "toml" -> "application/toml"
            "csv" -> "text/csv"
            "tsv" -> "text/tab-separated-values"
            else -> null
        }
    }
}
