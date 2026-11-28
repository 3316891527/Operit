package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.MessageSection
import com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec

/** Boundaries are canonical display prefixes, never offsets into the raw replay stream. */
internal class SteeredTranscript {
    private data class Boundary(val displayPrefix: String, val nextTimestamp: Long, val toolCallCount: Int)

    /**
     * Only the newest boundary is ever read, so older prefixes are dropped as newer ones arrive.
     *
     * A boundary prefix is the whole displayed turn up to that point, and one turn can publish many
     * of them: keeping every prefix would hold the entire turn once per boundary, which is most of
     * what a long tool-heavy turn retains in memory.
     */
    private var boundary: Boundary? = null
    private var rootTimestamp: Long? = null
    private var segmentStream: SteeredSegmentStream? = null
    /** The turn ended and its rows are saved; its text is let go, only row times stay. */
    private var released = false
    fun finalTimestamp(original: Long): Long =
        if (rootTimestamp == original) boundary?.nextTimestamp ?: original else original
    fun closeStream() = segmentStream?.close()

    /**
     * Called once the turn has ended and its rows are saved. A long steered turn's text would
     * otherwise stay in memory until the chat's next turn; a late snapshot of the ended turn is
     * then ignored instead of being written over its saved rows.
     */
    fun release() {
        segmentStream?.close()
        segmentStream = null
        boundary = boundary?.let { Boundary("", it.nextTimestamp, 0) }
        released = boundary != null
    }

    fun clear() {
        segmentStream?.close()
        segmentStream = null
        boundary = null
        rootTimestamp = null
        released = false
    }

    fun add(displayPrefix: String, nextTimestamp: Long, assistantTimestamp: Long) {
        if (rootTimestamp == null) rootTimestamp = assistantTimestamp
        require(rootTimestamp == assistantTimestamp)
        require(boundary?.let { displayPrefix.startsWith(it.displayPrefix) } != false)
        val toolCallCount = MessageSectionCodec.parse(displayPrefix).count { it is MessageSection.ToolCall }
        boundary = Boundary(displayPrefix, nextTimestamp, toolCallCount)
        segmentStream?.close()
        segmentStream = SteeredSegmentStream()
    }

    fun project(input: ChatMessage): List<ChatMessage> {
        val message = if (input.displayMode ==
            com.ai.assistance.operit.data.model.ChatMessageDisplayMode.ASSISTANT_INTERMEDIATE
        ) input.copy(
            inputTokens = 0, outputTokens = 0, cachedInputTokens = 0,
            waitDurationMs = 0, outputDurationMs = 0, completedAt = 0,
        ) else input
        val lastBoundary = boundary
        if (lastBoundary == null) {
            val hasTiming = message.toolTimingState != null || message.toolTimingStartIndex != 0
            return listOf(if (message.contentStream == null && hasTiming) message.freezeToolTimings() else message)
        }
        if (message.timestamp != rootTimestamp) return listOf(message)
        if (released) return emptyList()
        // 已封存的分段不再投影；转向之前捕获的旧快照也不能覆盖当前分段。
        if (!message.content.startsWith(lastBoundary.displayPrefix)) return emptyList()

        val suffix = message.content.substring(lastBoundary.displayPrefix.length)
        val streaming = message.contentStream != null
        segmentStream?.update(suffix)
        if (!streaming) segmentStream?.close()

        // 只投影源消息已有的快照；实时记录留在同一个整轮 holder，避免正文不变时计时冻结。
        val savedCalls = message.sections.filterIsInstance<MessageSection.ToolCall>()
        var localCallIndex = 0
        val projectedSections = MessageSectionCodec.parse(suffix).map { section ->
            if (section !is MessageSection.ToolCall) section else {
                val local = localCallIndex++
                val saved = savedCalls.getOrNull(lastBoundary.toolCallCount + local)
                    ?.takeIf { it.raw == section.raw }
                section.copy(execution = saved?.execution?.copy(callIndex = local))
            }
        }
        val projected = message.copy(
            content = suffix, timestamp = lastBoundary.nextTimestamp,
            sections = projectedSections,
            toolTimingStartIndex = message.toolTimingStartIndex + lastBoundary.toolCallCount,
            contentStream = if (streaming) segmentStream?.stream else null,
        )
        return listOf(if (streaming) projected else projected.freezeToolTimings())
    }
}
