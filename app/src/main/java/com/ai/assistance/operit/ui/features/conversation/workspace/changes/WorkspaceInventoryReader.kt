package com.ai.assistance.operit.ui.features.conversation.workspace.changes

/** NUL 字段允许跨传输页和 UTF-8 字节边界；只保留尚未结束的一个字段。 */
internal class WorkspaceInventoryReader {
    private var tail = byteArrayOf()
    private var name: String? = null

    suspend fun accept(bytes: ByteArray, onFile: suspend (String, String) -> Unit) {
        val data = if (tail.isEmpty()) bytes else tail + bytes
        var start = 0
        for (end in data.indices) if (data[end] == 0.toByte()) {
            val value = data.copyOfRange(start, end).toString(Charsets.UTF_8)
            val path = name
            if (path == null) {
                require(value.isNotEmpty() && !value.startsWith('/') && value.split('/').none { it == ".." || it == "." }) { "Invalid workspace inventory path" }
                name = value
            } else {
                onFile(path, value)
                name = null
            }
            start = end + 1
        }
        tail = data.copyOfRange(start, data.size)
        require(tail.size <= 65536) { "Workspace inventory field exceeds limit" }
    }

    fun finish() { require(tail.isEmpty() && name == null) { "Incomplete workspace inventory" } }
}