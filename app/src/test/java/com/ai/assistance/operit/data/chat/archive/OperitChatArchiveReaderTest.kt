package com.ai.assistance.operit.data.chat.archive

import com.ai.assistance.operit.data.model.MessageSection
import java.io.StringReader
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OperitChatArchiveReaderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun legacyContentAndVariantsSurviveMetadataAfterMessages() = runBlocking {
        val directory = temporary.newFolder()
        val input = """{"archiveType":"operit_chat_archive","formatVersion":2,"chats":[{
            "messages":[{"baseMessage":{"sender":"ai","content":"<think>草稿</think>正文\n<attachment id=\"a\">原文</attachment>","timestamp":1794984546753,"selectedVariantIndex":1,"inputTokens":15604,"outputTokens":58,"sentAt":1794984546741,"outputDurationMs":38,"completedAt":1794984553073},
            "variants":[{"variantIndex":1,"content":"候选","provider":"provider","modelName":"model","waitDurationMs":6286}]}],
            "title":"旧备份","id":"legacy","locked":true,"pinned":true,"group":"资料","usageBreakdownJson":"{}"}]}"""
        var visited = 0
        OperitChatArchiveReader(json, directory).read(StringReader(input), {}, { staged ->
            assertEquals("legacy", staged.metadata.id)
            assertTrue(staged.metadata.messages.isEmpty())
            assertTrue(staged.metadata.locked && staged.metadata.pinned)
            assertEquals("资料", staged.metadata.group)
            assertEquals("{}", staged.metadata.usageBreakdownJson)
            staged.forEachMessage { index, message ->
                visited++
                assertEquals(0, index)
                assertEquals(1794984546753L, message.baseMessage.timestamp)
                assertEquals(15604L, message.baseMessage.inputTokens)
                assertEquals(38L, message.baseMessage.outputDurationMs)
                assertTrue(message.baseMessage.sections.first() is MessageSection.Thinking)
                assertTrue(message.baseMessage.content.contains("<attachment id=\"a\">原文</attachment>"))
                assertEquals("候选", (message.variants.single().sections.single() as MessageSection.Text).content)
                assertEquals(6286L, message.variants.single().waitDurationMs)
            }
        })
        assertEquals(1, visited)
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun currentSectionsAndLegacyTypeNamesDecodeWithoutContentReencoding() = runBlocking {
        val directory = temporary.newFolder()
        val input = """{"folders":[],"formatVersion":3,"chats":[{"id":"current","title":"当前备份","archived":true,"messages":[{"baseMessage":{"sender":"ai","content":"忽略此旧字段","sections":[{"type":"com.ai.assistance.operit.data.model.MessageSection.Text","content":"正文"},{"type":"tool_call","name":"read_file","params":{"path":"a.txt"},"raw":"<tool name=\"read_file\"></tool>","execution":null}],"timestamp":7},"variants":[]}]}]}"""
        OperitChatArchiveReader(json, directory).read(StringReader(input), { assertTrue(it.isEmpty()) }, { staged ->
            assertTrue(staged.metadata.archived)
            staged.forEachMessage { _, message ->
                assertEquals(2, message.baseMessage.sections.size)
                assertEquals("正文", (message.baseMessage.sections.first() as MessageSection.Text).content)
                assertEquals("read_file", (message.baseMessage.sections.last() as MessageSection.ToolCall).name)
                assertFalse(message.baseMessage.content.contains("忽略此旧字段"))
            }
        })
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun legacyTopLevelArraysStillAcceptFlatMessages() = runBlocking {
        val directory = temporary.newFolder()
        val input = """[{"messages":[{"sender":"user","content":"旧数组","timestamp":8}],"id":"flat","title":"聊天"}]"""
        OperitChatArchiveReader(json, directory).read(StringReader(input), {}, { staged ->
            staged.forEachMessage { _, message -> assertEquals("旧数组", message.baseMessage.content); assertTrue(message.variants.isEmpty()) }
        })
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun aLongChatIsConsumedInOrderWhileMetadataHoldsNoMessages() = runBlocking {
        val directory = temporary.newFolder()
        val source = temporary.newFile()
        source.bufferedWriter().use { writer ->
            writer.write("{\"chats\":[{\"messages\":[")
            repeat(2000) { index ->
                if (index > 0) writer.write(",")
                writer.write("{\"baseMessage\":{\"sender\":\"user\",\"content\":\"${"x".repeat(512)}\",\"timestamp\":$index}}")
            }
            writer.write("],\"id\":\"large\",\"title\":\"大聊天\"}]}")
        }
        var visited = 0
        OperitChatArchiveReader(json, directory).read(source.reader(), {}, { staged ->
            assertEquals(2000, staged.messageCount)
            assertTrue(staged.metadata.messages.isEmpty())
            assertEquals(1, directory.listFiles()!!.size)
            staged.forEachMessage { index, message -> assertEquals(visited++, index); assertEquals(index.toLong(), message.baseMessage.timestamp) }
        })
        assertEquals(2000, visited)
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun malformedInputIsNotPublishedAndItsStagingFileIsDeleted() = runBlocking {
        val directory = temporary.newFolder()
        var published = false
        try {
            OperitChatArchiveReader(json, directory).read(StringReader("""{"chats":[{"messages":[{"baseMessage":{"sender":"user","content":"a"}}],"id":"a","title":"broken"""), {}, { published = true })
            fail("损坏的会话不能交给保存层")
        } catch (_: java.io.IOException) { }
        assertFalse(published)
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun consumerFailureAndCancellationBothDeleteTheStagingFile() = runBlocking {
        val input = """[{"id":"a","title":"聊天","messages":[{"sender":"user","content":"a"}]}]"""
        for (failure in listOf(IllegalStateException("保存失败"), CancellationException("取消"))) {
            val directory = temporary.newFolder()
            try {
                OperitChatArchiveReader(json, directory).read(StringReader(input), {}, { throw failure })
                fail("应传播保存失败或取消")
            } catch (caught: Exception) { assertSame(failure, caught) }
            assertTrue(directory.listFiles()!!.isEmpty())
        }
    }
}
