package com.ai.assistance.operit.core.chat.tools

import com.ai.assistance.operit.core.tools.protocol.ToolResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.ai.assistance.operit.core.chat.protocol.ConversationMarkupManager

/** The same finalized XML is published to the saved stream and used by the next model request. */
data class ToolExecutionBatch(val results: List<ToolResult>, val message: String)

/**
 * Execution may finish out of order, but saved XML is matched by tool name when history is rebuilt.
 * Publish only a completed prefix of the original calls so same-name results cannot exchange owners.
 * Indexes belong to this batch, not to tool names or invocation equality.
 */
internal class OrderedToolResults(
    count: Int,
    private val publish: suspend (String) -> Unit,
    private val publishAfterResult: suspend (String) -> Unit = publish
) {
    private val mutex = Mutex()
    private val results = arrayOfNulls<ToolResult>(count)
    private val markup = arrayOfNulls<String>(count)
    private val afterResult = arrayOfNulls<List<String>>(count)
    private var next = 0

    suspend fun complete(index: Int, result: ToolResult, afterResult: List<String> = emptyList()) {
        mutex.withLock {
            check(results[index] == null) { "Tool result already completed at index $index" }
            results[index] = result
            this.afterResult[index] = afterResult
            // Generate the random XML tag and bound the payload once, for both consumers.
            markup[index] = ConversationMarkupManager.formatToolResultForMessage(result)
            while (next < results.size && results[next] != null) {
                publish(checkNotNull(markup[next]))
                this.afterResult[next].orEmpty().forEach { publishAfterResult(it) }
                next++
            }
        }
    }

    suspend fun finish(): ToolExecutionBatch = mutex.withLock {
        check(next == results.size) { "Tool batch has unpublished results" }
        ToolExecutionBatch(
            results = results.map { checkNotNull(it) },
            message = markup.joinToString("\n") { checkNotNull(it) }
        )
    }
}
