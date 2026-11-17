package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import androidx.annotation.DrawableRes
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.archive.workspaceArchiveKind
import java.util.Locale

internal enum class WorkspaceThumbnailKind { IMAGE, APK }

/** 类型识别不读取文件；大小写、特殊文件名和多重后缀在所有列表保持一致。 */
@DrawableRes
internal fun workspaceFileTypeIcon(path: String): Int {
    val name = path.substringAfterLast('/').lowercase(Locale.ROOT)
    if (name == "dockerfile" || name.startsWith("dockerfile.")) return R.drawable.workspace_file_docker
    if (name in setOf(".gitignore", ".gitattributes", ".gitmodules", ".gitkeep")) return R.drawable.workspace_file_git
    if (name == "gradlew" || name == "gradlew.bat") return R.drawable.workspace_file_gradle
    if (name == "cmakelists.txt" || name == "makefile" || name == ".env" || name.startsWith(".env."))
        return R.drawable.workspace_file_settings
    if (name.endsWith(".gradle.kts") || name.endsWith(".gradle")) return R.drawable.workspace_file_gradle
    val extension = name.substringAfterLast('.', "")
    if (extension == "apk") return R.drawable.workspace_file_android
    if (workspaceArchiveKind(name) != null) return R.drawable.workspace_file_zip
    return when (extension) {
        "kt", "kts" -> R.drawable.workspace_file_kotlin
        "java", "class" -> R.drawable.workspace_file_java
        "py", "pyi", "pyw" -> R.drawable.workspace_file_python
        "ts", "tsx", "mts", "cts" -> R.drawable.workspace_file_typescript
        "js", "jsx", "mjs", "cjs" -> R.drawable.workspace_file_javascript
        "json", "json5", "jsonl", "ipynb" -> R.drawable.workspace_file_json
        "xml", "xsd", "xsl", "xslt", "pom" -> R.drawable.workspace_file_xml
        "md", "markdown", "mdx" -> R.drawable.workspace_file_markdown
        "html", "htm", "xhtml", "vue", "svelte" -> R.drawable.workspace_file_html
        "css", "scss", "sass", "less" -> R.drawable.workspace_file_css
        "c", "h" -> R.drawable.workspace_file_c
        "cpp", "cc", "cxx", "hpp", "hxx", "hh" -> R.drawable.workspace_file_cpp
        "cs", "csx" -> R.drawable.workspace_file_csharp
        "go", "mod", "sum" -> R.drawable.workspace_file_go
        "rs" -> R.drawable.workspace_file_rust
        "rb", "gemspec", "rake" -> R.drawable.workspace_file_ruby
        "php", "phtml" -> R.drawable.workspace_file_php
        "swift" -> R.drawable.workspace_file_swift
        "dart" -> R.drawable.workspace_file_dart
        "sh", "bash", "zsh", "fish", "bat", "cmd", "ps1" -> R.drawable.workspace_file_console
        "yaml", "yml" -> R.drawable.workspace_file_yaml
        "sql", "db", "sqlite", "sqlite3" -> R.drawable.workspace_file_database
        "toml", "ini", "cfg", "conf", "config", "properties", "editorconfig", "env" -> R.drawable.workspace_file_settings
        "png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif", "avif", "svg", "ico", "tiff", "tif" -> R.drawable.workspace_file_image
        "pdf" -> R.drawable.workspace_file_pdf
        "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus" -> R.drawable.workspace_file_audio
        "mp4", "m4v", "mkv", "mov", "webm", "avi", "3gp" -> R.drawable.workspace_file_video
        "ttf", "otf", "woff", "woff2" -> R.drawable.workspace_file_font
        else -> R.drawable.workspace_file_document
    }
}

internal fun workspaceThumbnailKind(path: String): WorkspaceThumbnailKind? =
    when (path.substringAfterLast('.').lowercase(Locale.ROOT)) {
        "apk" -> WorkspaceThumbnailKind.APK
        "png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif", "avif", "svg" -> WorkspaceThumbnailKind.IMAGE
        else -> null
    }
