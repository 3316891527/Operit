package com.ai.assistance.operit.ui.features.conversation.workspace.editor.highlight

import android.content.Context
import com.ai.assistance.operit.ui.features.conversation.workspace.editor.textmate.TextMateCatalog
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

internal object EditorHighlightFiles {
    private const val MaxFileBytes = 512 * 1024
    private const val MaxTotalBytes = 2 * 1024 * 1024

    fun directory(context: Context): File = File(context.filesDir, "config/editor/highlighting")

    /** 文件仅在打开或配置变动时读取，普通输入不会重复扫描目录。 */
    fun load(directory: File): EditorHighlightConfiguration {
        directory.mkdirs()
        require(directory.isDirectory) { "Cannot create highlighting directory: $directory" }
        val files = requireNotNull(directory.listFiles()) { "Cannot list highlighting directory: $directory" }
            .filter { it.name.endsWith(".json") }.sortedBy { it.name }
        require(files.size <= 64) { "Highlighting directory exceeds 64 JSON files" }
        var totalBytes = 0
        var global = EditorLanguageHighlight("_global")
        val languages = LinkedHashMap<String, EditorLanguageHighlight>()
        val parser = EditorHighlightConfigParser()
        for (file in files) {
            val name = file.name.removeSuffix(".json")
            require(file.isFile && (name == "_global" || name.matches(Regex("[a-z0-9][a-z0-9_-]{0,79}")))) { "Invalid highlighting file: ${file.name}" }
            val bytes = file.inputStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MaxFileBytes) { "${file.name} exceeds 512 KiB" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            totalBytes += bytes.size
            require(totalBytes <= MaxTotalBytes) { "Highlighting configuration exceeds 2 MiB" }
            val source = try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            } catch (failure: CharacterCodingException) {
                throw IllegalArgumentException("${file.name}: invalid UTF-8", failure)
            }
            // 新建文件尚未填写配置；保留文件和容量校验，等保存有效内容后再参与加载。
            if (source.isBlank()) continue
            val id = if (name == "_global") name else TextMateCatalog.canonicalId(name)
            val parsed = parser.parse(id, source)
            if (id == "_global") global = parsed else {
                require(languages.put(id, parsed) == null) { "Multiple highlighting files refer to '$id'" }
            }
        }
        return EditorHighlightConfiguration(global, languages).also { it.validate() }
    }
}
