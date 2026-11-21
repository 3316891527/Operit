package com.ai.assistance.operit.core.agent

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.chat.hooks.mergeAdjacentTurns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentContextHistoryTest {
    private fun user(text: String) = PromptTurn(PromptTurnKind.USER, text)
    private fun assistant(text: String) = PromptTurn(PromptTurnKind.ASSISTANT, text)
    private fun event(text: String) = user(text).copy(metadata = mapOf(SubagentContextHistory.EVENT to true))

    @Test
    fun forkArgumentsAreCanonicalAndBounded() {
        assertEquals(SubagentFork.None, SubagentFork.parse(" NONE "))
        assertEquals(SubagentFork.All, SubagentFork.parse("All"))
        assertEquals("1", SubagentFork.parse("01").parameter)
        assertEquals(SubagentFork.LastTurns(64), SubagentFork.parse("64"))
        for (invalid in listOf("", "0", "-1", "65", "1.5", "recent", "999999999999")) {
            assertThrows(invalid, IllegalArgumentException::class.java) { SubagentFork.parse(invalid) }
        }
    }

    @Test
    fun independentTasksDoNotRequireAnyParentContent() {
        assertEquals(emptyList<PromptTurn>(), SubagentContextHistory.select(listOf(user("私有背景")), SubagentFork.None))
        assertNull(SubagentContextHistory.render(emptyList(), SubagentFork.None))
    }

    @Test
    fun lastTurnsCountRealUserInputsAndExcludeHostEvents() {
        val history = listOf(
            user("较早要求"), assistant("较早结论"),
            user("第二轮"), assistant("第二轮结论"),
            event("旧子代理报告"),
            user("宿主状态").copy(metadata = mapOf(SubagentContextHistory.INTERMEDIATE to true)),
            user("当前要求"), event("[Subagent report saved]报告"),
        )
        assertEquals(listOf("第二轮", "第二轮结论", "当前要求"),
            SubagentContextHistory.select(history, SubagentFork.LastTurns(2)).map { it.content })
        assertEquals(listOf("当前要求"),
            SubagentContextHistory.select(history, SubagentFork.LastTurns(1)).map { it.content })
    }

    @Test
    fun allPreservesSummaryAndUserConstraintsButNotSystemToolsOrReasoning() {
        val history = listOf(
            PromptTurn(PromptTurnKind.SYSTEM, "父系统提示"),
            PromptTurn(PromptTurnKind.SUMMARY, "已确认约束", "old", mapOf("provider" to "private")),
            user("用户约束").copy(toolName = "old", metadata = mapOf("signature" to "private")),
            PromptTurn(PromptTurnKind.TOOL_CALL, "危险工具调用"),
            PromptTurn(PromptTurnKind.TOOL_RESULT, "工具返回"),
            assistant("<think>私有推理</think>最终结论").copy(metadata = mapOf("signature" to "private")),
            event("宿主报告"),
        )
        val selected = SubagentContextHistory.select(history, SubagentFork.All)
        assertEquals(listOf("已确认约束", "用户约束", "最终结论"), selected.map { it.content })
        assertTrue(selected.all { it.metadata.isEmpty() && it.toolName == null })
    }

    @Test
    fun rawAssistantTranscriptKeepsOnlyTextAfterTheLastTool() {
        val raw = "准备处理<tool name=\"read_file\"><param name=\"path\">secret</param></tool>" +
            "<tool_result name=\"read_file\">private result</tool_result><think>private reasoning</think>" +
            "<meta provider=\"test\">private signature</meta>最终答复"
        assertEquals(listOf("最终答复"), SubagentContextHistory.select(listOf(assistant(raw)), SubagentFork.All).map { it.content })
        val tokenized = "<think token=\"x\">私有推理</think>仍是推理</think token=\"x\">最终答复"
        assertEquals(listOf("最终答复"), SubagentContextHistory.select(listOf(assistant(tokenized)), SubagentFork.All).map { it.content })
        assertTrue(SubagentContextHistory.select(listOf(assistant("<think token=\"x\">未闭合推理")), SubagentFork.All).isEmpty())
    }

    @Test
    fun splitToolReplayCannotTurnIntermediateTextIntoAFinalAnswerOrAUserTurn() {
        val segments = listOf(
            assistant("计划调用工具"),
            PromptTurn(PromptTurnKind.TOOL_CALL, "调用"),
            PromptTurn(PromptTurnKind.TOOL_RESULT, "返回"),
            user("宿主告警"), assistant("最终答复"),
        )
        val restored = SubagentContextHistory.restoreSplitAssistantMetadata(segments, mapOf("source" to 1))
        assertEquals(listOf("最终答复"), SubagentContextHistory.select(restored, SubagentFork.All).map { it.content })
        assertThrows(IllegalArgumentException::class.java) { SubagentContextHistory.select(restored, SubagentFork.LastTurns(1)) }
        assertTrue(restored.all { it.metadata["source"] == 1 })
    }

    @Test
    fun persistedIntermediateAssistantRowsStayExcludedAfterReplay() {
        val restored = SubagentContextHistory.restoreSplitAssistantMetadata(
            listOf(assistant("尚未完成的文字")), mapOf(SubagentContextHistory.INTERMEDIATE to true),
        )
        assertTrue(SubagentContextHistory.select(restored, SubagentFork.All).isEmpty())
    }

    @Test
    fun hostReportsDoNotMergeWithOrHideTheActualUserRequest() {
        val merged = listOf(user("真实要求"), event("宿主报告"), user("后续要求"))
            .mergeAdjacentTurns(SubagentContextHistory::canMergeUserTurns)
        assertEquals(3, merged.size)
        assertEquals(listOf("真实要求", "后续要求"), SubagentContextHistory.select(merged, SubagentFork.All).map { it.content })
        assertTrue(SubagentContextHistory.canMergeUserTurns(user("a"), user("b")))
        assertFalse(SubagentContextHistory.canMergeUserTurns(event("a"), user("b")))
    }

    @Test
    fun durableReportMarkersAreExcludedEvenWithoutSourceMetadata() {
        assertEquals(listOf("真实要求"), SubagentContextHistory.select(
            listOf(user("真实要求"), user("[Subagent report stable-id]旧报告")), SubagentFork.LastTurns(1),
        ).map { it.content })
    }

    @Test
    fun aForkCannotSilentlyBecomeAnEmptyBackground() {
        assertThrows(IllegalArgumentException::class.java) { SubagentContextHistory.render(emptyList(), SubagentFork.All) }
        assertThrows(IllegalArgumentException::class.java) {
            SubagentContextHistory.render(listOf(PromptTurn(PromptTurnKind.SUMMARY, "总结")), SubagentFork.LastTurns(1))
        }
        assertTrue(requireNotNull(SubagentContextHistory.render(listOf(PromptTurn(PromptTurnKind.SUMMARY, "总结")), SubagentFork.All)).contains("总结"))
    }

    @Test
    fun inheritedBackgroundHasAnExplicitBoundaryBeforeTheNewAssignment() {
        val rendered = requireNotNull(SubagentContextHistory.render(listOf(user("保留约束")), SubagentFork.All))
        assertTrue(rendered.indexOf("保留约束") < rendered.indexOf("# End of inherited parent context"))
        assertTrue(rendered.endsWith("The next user message is your new task brief."))
    }

    @Test
    fun contextSizeIncludesItsEnvelopeAndOverflowIsRejectedWithoutTruncation() {
        val envelope = requireNotNull(SubagentContextHistory.render(listOf(user("x")), SubagentFork.All)).length - 1
        val fits = "x".repeat(SubagentContextHistory.MAX_CHARACTERS - envelope)
        assertEquals(SubagentContextHistory.MAX_CHARACTERS,
            requireNotNull(SubagentContextHistory.render(listOf(user(fits)), SubagentFork.All)).length)
        assertThrows(IllegalArgumentException::class.java) {
            SubagentContextHistory.render(listOf(user(fits + "x")), SubagentFork.All)
        }
    }
}
