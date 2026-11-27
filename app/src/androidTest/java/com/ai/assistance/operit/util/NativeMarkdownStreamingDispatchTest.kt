package com.ai.assistance.operit.util.streamnative

import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamGroup
import com.ai.assistance.operit.util.stream.stream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeMarkdownStreamingDispatchTest {
    private data class GroupSnapshot(
        val tag: MarkdownProcessorType?,
        val text: String,
        val continuesPrevious: Boolean,
    )

    private suspend fun snapshots(
        groups: Stream<StreamGroup<MarkdownProcessorType?>>,
    ): List<GroupSnapshot> {
        val result = mutableListOf<GroupSnapshot>()
        groups.collect { group ->
            val content = StringBuilder()
            group.stream.collect { content.append(it) }
            result.add(GroupSnapshot(group.tag, content.toString(), group.continuesPrevious))
        }
        return result
    }

    @Test
    fun mainCallerKeepsItsContextWhileNativeBlocksAreCollected() = runBlocking {
        withTimeout(10_000L) {
            withContext(Dispatchers.Main) {
                val heartbeat = CompletableDeferred<Unit>()
                val sourceText = "<tool_result name=\"read\">" + "工具结果".repeat(16_384) + "</tool_result>"
                val source = stream {
                    assertSame(Looper.getMainLooper(), Looper.myLooper())
                    // 只核对主线程调用上下文和排队消息能完成，不把调度先后当作性能阈值。
                    Handler(Looper.getMainLooper()).post { heartbeat.complete(Unit) }
                    emit(sourceText)
                    assertSame(Looper.getMainLooper(), Looper.myLooper())
                    heartbeat.await()
                }
                val groups = snapshots(source.nativeMarkdownSplitByBlock())
                assertEquals(sourceText, groups.joinToString("") { it.text })
                assertEquals(MarkdownProcessorType.XML_BLOCK, groups.single().tag)
            }
        }
    }

    @Test
    fun naturalCompletionFlushesTailAndStopsLongIntervalTimerForBothApis() = runBlocking {
        withTimeout(10_000L) {
            withContext(Dispatchers.Main) {
                val text = "<tool_result name=\"read\">完整尾部</tool_result>"
                // 定时间隔远大于超时，正常结束必须主动刷新并终止定时任务。
                val chunks = stream { emit(text.take(17)); emit(text.drop(17)) }
                val chars = stream { text.forEach { emit(it) } }
                val chunkGroups = snapshots(chunks.nativeMarkdownSplitByBlock(flushIntervalMs = 60_000L))
                val charGroups = snapshots(chars.nativeMarkdownSplitByBlock(flushIntervalMs = 60_000L))
                assertEquals(text, chunkGroups.joinToString("") { it.text })
                assertEquals(chunkGroups, charGroups)
                val inline = stream { emit("普通尾部") }
                assertEquals("普通尾部", snapshots(inline.nativeMarkdownSplitByInline(flushIntervalMs = 60_000L)).joinToString("") { it.text })
            }
        }
    }

    @Test
    fun cancellationStopsTimerBeforeNextNativeSession() = runBlocking {
        withTimeout(10_000L) {
            withContext(Dispatchers.Main) {
                val sourceClosed = CompletableDeferred<Unit>()
                lateinit var parsingJob: Job
                val source = stream {
                    try {
                        // 取消可在解析期间或之后送达，两种顺序都必须完成会话清理。
                        Handler(Looper.getMainLooper()).post { parsingJob.cancel() }
                        emit("<tool_result name=\"read\">" + "取消中的结果".repeat(16_384) + "</tool_result>")
                        awaitCancellation()
                    } finally {
                        sourceClosed.complete(Unit)
                    }
                }
                parsingJob = launch(start = CoroutineStart.LAZY) {
                    snapshots(source.nativeMarkdownSplitByBlock(flushIntervalMs = 60_000L, maxDeltaChars = 1))
                }
                parsingJob.start()
                parsingJob.join()
                assertTrue(parsingJob.isCancelled)
                assertTrue(sourceClosed.isCompleted)
                val next = stream { emit("<tool_result name=\"next\">新会话</tool_result>") }
                assertEquals("<tool_result name=\"next\">新会话</tool_result>", snapshots(next.nativeMarkdownSplitByBlock()).joinToString("") { it.text })
            }
        }
    }

    @Test
    fun upstreamFailurePropagatesAndDoesNotKeepFlushTimerAlive() = runBlocking {
        withTimeout(10_000L) {
            val expected = IllegalStateException("上游读取失败")
            var observed: Throwable? = null
            try {
                withContext(Dispatchers.Main) {
                    val source = stream<String> {
                        emit("<tool_result name=\"read\">尚未发布")
                        throw expected
                    }
                    snapshots(source.nativeMarkdownSplitByBlock(flushIntervalMs = 60_000L))
                }
            } catch (error: IllegalStateException) {
                observed = error
            }
            assertSame(expected, observed)
        }
    }

    @Test
    fun chunkRollbackContinuesOnlyTheOpenXmlBlock() = runBlocking {
        withTimeout(10_000L) {
            val openPrefix = "<tool_result name=\"read\">保留前缀"
            val tail = "新增尾部</tool_result>"
            val continued = snapshots(
                stream { emit(tail) }.nativeMarkdownSplitByBlock(
                    flushIntervalMs = null,
                    maxDeltaChars = null,
                    initialContent = openPrefix,
                )
            )
            assertEquals(listOf(GroupSnapshot(MarkdownProcessorType.XML_BLOCK, tail, true)), continued)
            val nextBlock = "<tool_result name=\"next\">新的结果</tool_result>"
            val separate = snapshots(
                stream { emit(nextBlock) }.nativeMarkdownSplitByBlock(
                    flushIntervalMs = null,
                    maxDeltaChars = null,
                    initialContent = openPrefix + "</tool_result>",
                )
            )
            assertEquals(listOf(GroupSnapshot(MarkdownProcessorType.XML_BLOCK, nextBlock, false)), separate)
        }
    }
}
