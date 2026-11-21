package com.ai.assistance.operit.core.agent

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 每个任务单独原子写入，流式更新不会重写其它会话的历史。调用方串行化读写。 */
internal class SubagentTaskStore(context: Context) {
    private val directory = File(context.filesDir, "subagent_tasks")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): List<SubagentTask> {
        if (!directory.exists()) return emptyList()
        check(directory.isDirectory) { "Subagent storage is not a directory" }
        val files = requireNotNull(directory.listFiles()) { "Cannot list subagent storage" }
        return files.filter { it.name.endsWith(".json") || it.name.endsWith(".json.bak") }
            .map { it.name.removeSuffix(".bak").removeSuffix(".json") }.distinct().map { id ->
                UUID.fromString(id)
                val task = atomic(id).openRead().use { input -> json.decodeFromString<SubagentTask>(input.readBytes().toString(Charsets.UTF_8)) }
                check(task.id == id && task.schemaVersion == 1) { "Invalid subagent checkpoint: $id" }
                task
            }.sortedBy { it.createdAt }
    }

    fun save(task: SubagentTask) {
        UUID.fromString(task.id)
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create subagent storage" }
        val file = atomic(task.id)
        val stream = file.startWrite()
        try {
            stream.write(json.encodeToString(task).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Throwable) {
            file.failWrite(stream)
            throw error
        }
    }

    fun delete(id: String) {
        UUID.fromString(id)
        atomic(id).delete()
    }
    private fun atomic(id: String): AtomicFile = AtomicFile(File(directory, "$id.json"))
}