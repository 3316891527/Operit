package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.ai.assistance.operit.util.markdown.MarkdownNode
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.stream.Stream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RenderBatchCoordinatorTest {
    @Test
    fun requestWhileBatchIsPending_isIncludedWithoutAnotherInput() = runTest {
        var flushCount = 0
        val coordinator =
            RenderBatchCoordinator(
                scope = this,
                intervalMs = 200,
                onFlush = { flushCount++ },
            )

        coordinator.requestUpdate()
        advanceTimeBy(100)
        coordinator.requestUpdate()
        advanceUntilIdle()

        assertEquals(1, flushCount)
    }

    @Test
    fun requestDuringFlush_isDrainedBeforeCoordinatorBecomesIdle() = runTest {
        var flushCount = 0
        lateinit var coordinator: RenderBatchCoordinator
        coordinator =
            RenderBatchCoordinator(
                scope = this,
                intervalMs = 200,
                onFlush = {
                    flushCount++
                    if (flushCount == 1) {
                        coordinator.requestUpdate()
                    }
                },
            )

        coordinator.requestUpdate()
        advanceUntilIdle()

        assertEquals(2, flushCount)
    }

    @Test
    fun toolXmlTailMutation_isRenderedWithoutAnotherInput() = runTest {
        val nodes = mutableStateListOf<MarkdownNode>()
        val renderNodes = mutableStateListOf<MarkdownNodeStable>()
        val nodeAnimationStates = mutableStateMapOf<String, Boolean>()
        val updater =
            BatchNodeUpdater(
                nodes = nodes,
                renderNodes = renderNodes,
                nodeAnimationStates = nodeAnimationStates,
                xmlNodeStreams = mutableMapOf<Int, Stream<String>>(),
                rendererId = "tool-xml-test",
                scope = this,
            )
        val toolNode = MarkdownNode(type = MarkdownProcessorType.XML_BLOCK)
        nodes.add(toolNode)

        val prefix = "<tool name=\"shell\"><param name=\"command\">echo par"
        updater.appendBlockChunk(toolNode, prefix)
        advanceUntilIdle()
        assertEquals(prefix, renderNodes.single().content)

        val tail = "tial</param></tool>"
        updater.appendBlockChunk(toolNode, tail)
        advanceUntilIdle()

        assertEquals(prefix + tail, renderNodes.single().content)
    }

    @Test
    fun equalLengthBlockNodeReplacement_isRendered() = runTest {
        val nodes = mutableStateListOf(MarkdownNode(MarkdownProcessorType.PLAIN_TEXT, "x"))
        val renderNodes = mutableStateListOf<MarkdownNodeStable>()
        val updater =
            BatchNodeUpdater(
                nodes = nodes,
                renderNodes = renderNodes,
                nodeAnimationStates = mutableStateMapOf(),
                xmlNodeStreams = mutableMapOf<Int, Stream<String>>(),
                rendererId = "equal-length-block-test",
                scope = this,
            )

        updater.requestUpdate()
        advanceUntilIdle()

        nodes[0] = MarkdownNode(MarkdownProcessorType.BLOCK_LATEX, "x")
        updater.requestStructuralUpdate()
        advanceUntilIdle()

        assertEquals(MarkdownProcessorType.BLOCK_LATEX, renderNodes.single().type)
    }

    @Test
    fun equalLengthChildNodeReplacement_isRendered() = runTest {
        val parent = MarkdownNode(MarkdownProcessorType.PLAIN_TEXT, "x")
        parent.children.add(MarkdownNode(MarkdownProcessorType.PLAIN_TEXT, "x"))
        val nodes = mutableStateListOf(parent)
        val renderNodes = mutableStateListOf<MarkdownNodeStable>()
        val updater =
            BatchNodeUpdater(
                nodes = nodes,
                renderNodes = renderNodes,
                nodeAnimationStates = mutableStateMapOf(),
                xmlNodeStreams = mutableMapOf<Int, Stream<String>>(),
                rendererId = "equal-length-child-test",
                scope = this,
            )

        updater.requestUpdate()
        advanceUntilIdle()

        parent.children[0] = MarkdownNode(MarkdownProcessorType.INLINE_LATEX, "x")
        updater.requestStructuralUpdate()
        advanceUntilIdle()

        assertEquals(MarkdownProcessorType.INLINE_LATEX, renderNodes.single().children.single().type)
    }

    @Test
    fun tailUpdateReusesCompletedNodeSnapshots() = runTest {
        val nodes = mutableStateListOf<MarkdownNode>().apply {
            repeat(128) { add(MarkdownNode(MarkdownProcessorType.XML_BLOCK, "历史工具-$it")) }
        }
        val rendered = mutableStateListOf<MarkdownNodeStable>()
        val updater = BatchNodeUpdater(nodes, rendered, mutableStateMapOf(), mutableMapOf(), "dirty-tail", this)
        updater.requestUpdate()
        advanceUntilIdle()
        val previous = rendered.toList()

        updater.appendBlockChunk(nodes.last(), "新结果", nodes.lastIndex)
        advanceUntilIdle()

        previous.dropLast(1).forEachIndexed { index, snapshot -> assertSame(snapshot, rendered[index]) }
        assertEquals("历史工具-127新结果", rendered.last().content)
    }

    @Test
    fun earliestDirtyNodeAndSameLengthStructureAreBothPublished() = runTest {
        val nodes = mutableStateListOf(
            MarkdownNode(MarkdownProcessorType.PLAIN_TEXT, "保留"),
            MarkdownNode(MarkdownProcessorType.PLAIN_TEXT, "x"),
            MarkdownNode(MarkdownProcessorType.PLAIN_TEXT, "尾部"),
        )
        val rendered = mutableStateListOf<MarkdownNodeStable>()
        val updater = BatchNodeUpdater(nodes, rendered, mutableStateMapOf(), mutableMapOf(), "dirty-order", this)
        updater.requestUpdate()
        advanceUntilIdle()
        val retained = rendered.first()
        updater.appendBlockChunk(nodes[2], "新值", 2)
        nodes[1] = MarkdownNode(MarkdownProcessorType.BLOCK_LATEX, "x")
        updater.requestStructuralUpdate(1)
        advanceUntilIdle()
        assertSame(retained, rendered.first())
        assertEquals(MarkdownProcessorType.BLOCK_LATEX, rendered[1].type)
        assertEquals("尾部新值", rendered[2].content)
    }

    @Test
    fun completingXmlPublishesTheLastMutationBeforeTheTimer() = runTest {
        val node = MarkdownNode(MarkdownProcessorType.XML_BLOCK, "开始")
        val nodes = mutableStateListOf(node)
        val rendered = mutableStateListOf<MarkdownNodeStable>()
        val streams = mutableMapOf<Int, Stream<String>>()
        val updater = BatchNodeUpdater(nodes, rendered, mutableStateMapOf(), streams, "xml-finish", this)
        updater.appendBlockChunk(node, "结束", 0)
        updater.completeXmlBlock(0)
        assertEquals("开始结束", rendered.single().content)
        val completed = rendered.single()
        advanceUntilIdle()
        assertSame(completed, rendered.single())
    }
}
