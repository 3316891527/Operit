package com.ai.assistance.operit.data.chat.archive

import java.io.File
import java.io.IOException
import java.io.Reader
import java.io.StringReader
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class OperitChatStreamReaderTest {
    private val fields = setOf("id", "title", "folderPath", "lastUsedAt", "usageBreakdownJson", "archived")

    private fun withCache(block: suspend (File) -> Unit) = runBlocking {
        val directory = Files.createTempDirectory("operit-chat-import-test").toFile()
        try {
            block(directory)
            assertTrue("导入后不能遗留消息暂存文件", directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun archiveObjectKeepsFoldersAndStreamsEachMessage() = withCache { directory ->
        var folders = 0
        var chats = 0
        OperitChatStreamReader(directory, fields).read(StringReader("""
            {"folders":[{"id":"folder"}],"chats":[{"id":"a","title":"标题","messages":[
                {"baseMessage":{"sender":"user","content":"你好"}},
                {"baseMessage":{"sender":"ai","content":"回答"},"variants":[{"variantIndex":1}]}
            ]}]}
        """), onFolders = { folders = it.jsonArray.size }, onChat = { chat ->
            chats++
            assertTrue(chat.archived)
            assertEquals(2, chat.messageCount)
            assertEquals(JsonArray(emptyList()), chat.metadata["messages"])
            val senders = mutableListOf<String>()
            chat.forEachMessage { index, message ->
                assertEquals(senders.size, index)
                senders.add(message.jsonObject.getValue("baseMessage").jsonObject.getValue("sender").jsonPrimitive.content)
            }
            assertEquals(listOf("user", "ai"), senders)
        })
        assertEquals(1, folders)
        assertEquals(1, chats)
    }

    @Test fun legacyArrayUsesLegacyMessageShape() = withCache { directory ->
        OperitChatStreamReader(directory, fields).read(StringReader("""
            [{"id":"legacy","title":"旧会话","messages":[{"sender":"user","content":"原文"}]}]
        """), {}, { chat ->
            assertFalse(chat.archived)
            chat.forEachMessage { _, message ->
                assertEquals("原文", message.jsonObject.getValue("content").jsonPrimitive.content)
            }
        })
    }

    @Test fun messagesCanPrecedeAllChatMetadata() = withCache { directory ->
        OperitChatStreamReader(directory, fields).read(StringReader("""
            [{"messages":[{"content":"正文"}],"title":"后置标题","id":"late"}]
        """), {}, { chat ->
            assertEquals("late", chat.metadata.getValue("id").jsonPrimitive.content)
            assertEquals("后置标题", chat.metadata.getValue("title").jsonPrimitive.content)
            assertEquals(1, chat.messageCount)
        })
    }

    @Test fun unknownFieldsAreSkippedWithoutEnteringMetadata() = withCache { directory ->
        OperitChatStreamReader(directory, fields).read(StringReader("""
            {"unknown":{"deep":[1,2,3]},"chats":[{"title":"t","unknown":[{"large":"ignored"}],"messages":[]}]}
        """), {}, { chat ->
            assertFalse(chat.metadata.containsKey("unknown"))
            assertEquals(0, chat.messageCount)
        })
    }

    @Test fun numericPrecisionEscapesNullAndSectionsRemainUnchanged() = withCache { directory ->
        val message = """{"timestamp":9223372036854775807,"decimal":1.234567890123456789,"flag":true,"value":null,"sections":[{"text":"汉字😀\n\"\\\u0000"}]}"""
        OperitChatStreamReader(directory, fields).read(StringReader("""[{"title":"t","messages":[$message]}]"""), {}, { chat ->
            chat.forEachMessage { _, actual -> assertEquals(Json.parseToJsonElement(message), actual) }
        })
    }

    @Test fun chatsAreConsumedAndReleasedOneAtATime() = withCache { directory ->
        val titles = mutableListOf<String>()
        OperitChatStreamReader(directory, fields).read(StringReader("""
            [{"title":"a","messages":[1]},{"title":"b","messages":[2]}]
        """), {}, { chat ->
            assertEquals(1, directory.listFiles().orEmpty().size)
            titles.add(chat.metadata.getValue("title").jsonPrimitive.content)
            chat.forEachMessage { _, _ -> }
        })
        assertEquals(listOf("a", "b"), titles)
    }

    @Test fun duplicateMessageArraysKeepTheLastValue() = withCache { directory ->
        OperitChatStreamReader(directory, fields).read(StringReader("""
            [{"title":"t","messages":[1,2],"messages":[3]}]
        """), {}, { chat ->
            assertEquals(1, chat.messageCount)
            chat.forEachMessage { _, value -> assertEquals(3, value.jsonPrimitive.int) }
        })
    }

    @Test fun missingMessagesDoesNotBecomeAValidEmptyArray() = withCache { directory ->
        OperitChatStreamReader(directory, fields).read(StringReader("""[{"title":"missing"}]"""), {}, { chat ->
            assertFalse(chat.metadata.containsKey("messages"))
        })
    }

    @Test fun malformedLateMessageDoesNotReachTheSaveCallback() = withCache { directory ->
        var saved = false
        try {
            OperitChatStreamReader(directory, fields).read(StringReader("""
                [{"title":"broken","messages":[{"content":"first"},{"content":
            """), {}, { saved = true })
            fail("应拒绝被截断的消息")
        } catch (_: IOException) { }
        assertFalse(saved)
    }

    @Test fun nullMessageArrayFailsAndCleansCache() = withCache { directory ->
        try {
            OperitChatStreamReader(directory, fields).read(StringReader("""[{"title":"t","messages":null}]"""), {}, { fail() })
            fail("messages 必须为数组")
        } catch (_: IllegalStateException) { }
    }

    @Test fun callbackFailureCleansCacheAndDoesNotConsumeNextChat() = withCache { directory ->
        var calls = 0
        try {
            OperitChatStreamReader(directory, fields).read(StringReader("""
                [{"title":"a","messages":[1]},{"title":"b","messages":[2]}]
            """), {}, { calls++; error("写入失败") })
            fail()
        } catch (e: IllegalStateException) {
            assertEquals("写入失败", e.message)
        }
        assertEquals(1, calls)
    }

    @Test fun cancellationDuringMessageConsumptionCleansCache() = withCache { directory ->
        var count = 0
        try {
            OperitChatStreamReader(directory, fields).read(StringReader("""
                [{"title":"cancel","messages":[1,2,3]}]
            """), {}, { chat ->
                chat.forEachMessage { _, _ -> count++; throw CancellationException("停止") }
            })
            fail()
        } catch (_: CancellationException) { }
        assertEquals(1, count)
    }

    @Test fun cancellationDuringSpoolingCleansCache() = withCache { directory ->
        val source = object : Reader() {
            private var initial = true
            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                if (initial) {
                    initial = false
                    val prefix = """[{"title":"t","messages":["""
                    prefix.toCharArray().copyInto(buffer, offset)
                    return prefix.length
                }
                throw CancellationException("读取期间停止")
            }
            override fun close() { }
        }
        try {
            OperitChatStreamReader(directory, fields).read(source, {}, { fail() })
            fail()
        } catch (_: CancellationException) { }
    }

    @Test fun trailingJsonIsRejected() = withCache { directory ->
        try {
            OperitChatStreamReader(directory, fields).read(StringReader("[] []"), {}, {})
            fail()
        } catch (_: IllegalStateException) { }
    }

    @Test fun cacheCreationFailurePreventsImport() = withCache { directory ->
        val occupied = File(directory, "occupied").apply { writeText("file") }
        try {
            OperitChatStreamReader(occupied, fields).read(StringReader("[]"), {}, { fail() })
            fail()
        } catch (_: IllegalStateException) { }
        occupied.delete()
    }

    @Test fun hundredMiBConversationWorksWithoutAnEntireChatInHeap() = withCache { directory ->
        val repetitions = 25_600
        val content = "x".repeat(4_096)
        val fragment = """{"content":"$content","timestamp":123}"""
        val source = RepeatedMessageReader(repetitions, fragment)
        var count = 0
        OperitChatStreamReader(directory, fields).read(source, {}, { chat ->
            assertEquals(repetitions, chat.messageCount)
            assertTrue(chat.metadata.toString().length < 200)
            chat.forEachMessage { _, message ->
                assertEquals(4_096, message.jsonObject.getValue("content").jsonPrimitive.content.length)
                count++
            }
        })
        assertEquals(repetitions, count)
    }
}

/** 生成输入时不预先分配完整测试文件，受限堆回归才能覆盖真实累计内存风险。 */
private class RepeatedMessageReader(private val count: Int, private val message: String) : Reader() {
    private var part = 0
    private var offset = 0
    private var current = """[{"title":"large","messages":["""
    override fun read(buffer: CharArray, start: Int, length: Int): Int {
        if (length == 0) return 0
        while (offset == current.length) {
            part++
            if (part > count + 1) return -1
            current = if (part == count + 1) "]}]" else if (part == 1) message else ",$message"
            offset = 0
        }
        val copied = minOf(length, current.length - offset)
        current.toCharArray(buffer, start, offset, offset + copied)
        offset += copied
        return copied
    }
    override fun close() { }
}