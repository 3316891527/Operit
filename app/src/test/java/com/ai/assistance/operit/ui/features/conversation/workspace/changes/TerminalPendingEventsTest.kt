package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TerminalPendingEventsTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun origin(execution: String) = TerminalChangeOrigin("chat", "terminal", execution, "agent")

    @Test fun largeEventQueueDrainsInFixedBatchesWithoutFillingQueryCache() {
        TerminalObservationIndex(JdbcTerminalIndexDatabase(temporary.newFile())).use { index ->
            repeat(4096) { index.enqueue("files/$it", TerminalPendingChange(origin("run"))) }
            var count = 0
            while (index.hasPending()) {
                val page = index.takePending()
                assertTrue(page.isNotEmpty())
                assertTrue(page.size <= TerminalObservationIndex.PAGE_SIZE)
                count += page.size
            }
            assertEquals(4096, count)
            assertEquals(0, index.cachedEntries())
        }
    }

    @Test fun repeatedEventsMergeExecutionOwnershipAndKeepOriginalCreate() {
        TerminalObservationIndex(JdbcTerminalIndexDatabase(temporary.newFile())).use { index ->
            index.enqueue("file", TerminalPendingChange(origin("one"), true))
            index.enqueue("file", TerminalPendingChange(origin("two")))
            val pending = index.takePending().getValue("file")
            assertTrue(pending.createdInPlace)
            assertEquals("chat", pending.origin!!.chatId)
            assertEquals("agent", pending.origin.subagentExecutionId)
            assertNull(pending.origin.executionId)
        }
    }

    @Test fun trackedFileCannotReplaceAnAlreadyOwnedEvent() {
        TerminalObservationIndex(JdbcTerminalIndexDatabase(temporary.newFile())).use { index ->
            index.enqueue("file", TerminalPendingChange(origin("first")))
            index.enqueue("file", TerminalPendingChange(origin("second")), onlyWhenUnowned = true)
            assertEquals("first", index.takePending().getValue("file").origin!!.executionId)
        }
    }

    @Test fun parentBatchCanReadSpecificChildOwnershipFromLaterPages() {
        TerminalObservationIndex(JdbcTerminalIndexDatabase(temporary.newFile())).use { index ->
            index.enqueue("new", TerminalPendingChange(origin("parent"), true))
            repeat(256) { index.enqueue("new/child-$it", TerminalPendingChange(origin("child"))) }
            val first = index.takePending()
            assertTrue("new" in first)
            val queued = index.pendingAncestors("new/child-99")
            assertEquals("child", queued.getValue("new/child-99").origin!!.executionId)
            assertTrue(index.hasOwnedPendingAncestor("new/child-99/deep"))
        }
    }

    @Test fun restoredOlderBatchKeepsCreateWhileMergingNewNotifications() {
        TerminalObservationIndex(JdbcTerminalIndexDatabase(temporary.newFile())).use { index ->
            index.enqueue("file", TerminalPendingChange(origin("before"), true))
            val old = index.takePending().getValue("file")
            index.enqueue("file", TerminalPendingChange(origin("after")))
            index.enqueue("file", old, older = true)
            val restored = index.takePending().getValue("file")
            assertTrue(restored.createdInPlace)
            assertNull(restored.origin!!.executionId)
        }
    }
}