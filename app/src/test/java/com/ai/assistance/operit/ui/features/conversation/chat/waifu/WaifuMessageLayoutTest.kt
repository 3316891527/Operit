package com.ai.assistance.operit.ui.features.conversation.chat.waifu

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import org.junit.Assert.*
import org.junit.Test

class WaifuMessageLayoutTest {
    private fun reply(content: String = "正文", sentAt: Long = 100L) = ChatMessage(
        sender = "ai", content = content, sentAt = sentAt, roleName = "角色", provider = "provider", modelName = "model",
    )
    private fun node(type: MarkdownProcessorType, content: String = "内容") = MarkdownNodeStable(type, content, emptyList())

    @Test fun processAndFollowingReplyShareTheirHeader() {
        assertTrue(waifuJoinsPrevious(reply("<think>原文</think>"), reply(), true, true))
    }

    @Test fun consecutiveProcessRecordsShareTheirHeader() {
        assertTrue(waifuJoinsPrevious(reply("<think>原文</think>"), reply("<tool name=\"read_file\"/>"), true, true))
    }

    @Test fun independentSentencesKeepTheirOwnAvatars() {
        assertFalse(waifuJoinsPrevious(reply("第一句"), reply("第二句"), true, false))
    }

    @Test fun ordinaryModeAndMissingPreviousRowsDoNotJoin() {
        assertFalse(waifuJoinsPrevious(reply(), reply(), false, true))
        assertFalse(waifuJoinsPrevious(null, reply(), true, true))
    }

    @Test fun requestRoleModelProviderAndUserBoundariesRemainIndependent() {
        val previous = reply()
        listOf(reply(sentAt = 200), reply().copy(roleName = "其他角色"), reply().copy(modelName = "other"),
            reply().copy(provider = "other"), reply().copy(sender = "user")).forEach {
            assertFalse(waifuJoinsPrevious(previous, it, true, true))
        }
        assertFalse(waifuJoinsPrevious(previous.copy(sender = "user"), reply(), true, true))
        assertFalse(waifuJoinsPrevious(reply(sentAt = 0), reply(sentAt = 0), true, true))
    }

    @Test fun standaloneBlocksUseTheirOwnPresentation() {
        listOf(MarkdownProcessorType.IMAGE, MarkdownProcessorType.CODE_BLOCK, MarkdownProcessorType.TABLE,
            MarkdownProcessorType.BLOCK_LATEX, MarkdownProcessorType.HTML_BLOCK).forEach {
            assertTrue(isWaifuStandaloneNode(node(it)) { false })
        }
    }

    @Test fun inlineFormattingAndTextKeepTheChatShell() {
        listOf(MarkdownProcessorType.PLAIN_TEXT, MarkdownProcessorType.INLINE_CODE, MarkdownProcessorType.BOLD,
            MarkdownProcessorType.INLINE_LATEX, MarkdownProcessorType.LINK).forEach {
            assertFalse(isWaifuStandaloneNode(node(it)) { true })
        }
    }

    @Test fun registeredXmlCardsAndBuiltInContainersAreStandalone() {
        assertTrue(isWaifuStandaloneNode(node(MarkdownProcessorType.XML_BLOCK, "<a2ui/>")) { it == "a2ui" })
        listOf("html", "details", "detail", "search").forEach {
            assertTrue(isWaifuStandaloneNode(node(MarkdownProcessorType.XML_BLOCK, "<$it/>")) { false })
        }
        assertFalse(isWaifuStandaloneNode(node(MarkdownProcessorType.XML_BLOCK, "<unknown/>")) { false })
    }

    @Test fun internalProtocolTagsNeverBecomeStandaloneCardsThroughPlugins() {
        listOf("think", "thinking", "tool", "tool_result", "meta", "status", "font", "emotion").forEach {
            assertFalse(isWaifuStandaloneNode(node(MarkdownProcessorType.XML_BLOCK, "<$it/>")) { true })
        }
    }

    @Test fun mixedMessagesRemoveOnlyTheSpecialNodesShell() {
        val nodes = listOf(node(MarkdownProcessorType.PLAIN_TEXT, "前文"), node(MarkdownProcessorType.IMAGE, "![emotion](file:///表情.png)"),
            node(MarkdownProcessorType.CODE_BLOCK, "```a2ui\n{}\n```"), node(MarkdownProcessorType.PLAIN_TEXT, "后文"))
        assertEquals(listOf(false, true, true, false), nodes.map { isWaifuStandaloneNode(it) { false } })
    }
}
