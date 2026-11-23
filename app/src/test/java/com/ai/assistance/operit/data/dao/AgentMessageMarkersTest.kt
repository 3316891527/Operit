package com.ai.assistance.operit.data.dao

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`

class AgentMessageMarkersTest {
    private val dao = mock(MessageDao::class.java)

    @Test fun emptyMailboxDoesNotQueryHistory() = runBlocking {
        assertTrue(dao.findPersistedAgentMessageMarkers("root", emptySet()).isEmpty())
        verifyNoInteractions(dao)
    }

    @Test fun largeMailboxStaysWithinLegacySqliteBindLimit() = runBlocking {
        val markers = (0 until 2005).map { "[Agent message $it]" }.toSet()
        val batches = markers.chunked(900)
        batches.forEach { batch ->
            `when`(dao.getPersistedAgentMessageMarkers("root", batch)).thenReturn(batch)
        }

        assertEquals(markers, dao.findPersistedAgentMessageMarkers("root", markers))
        batches.forEach { verify(dao).getPersistedAgentMessageMarkers("root", it) }
        verifyNoMoreInteractions(dao)
    }

    @Test fun missingAndDuplicateRowsDoNotChangeTheRecoveredSet() = runBlocking {
        val marker = "[Subagent report persisted]"
        val pending = setOf(marker, "[Agent message pending]")
        `when`(dao.getPersistedAgentMessageMarkers("root", pending.toList()))
            .thenReturn(listOf(marker, marker))
        assertEquals(setOf(marker), dao.findPersistedAgentMessageMarkers("root", pending))
    }

    @Test fun databaseFailureDoesNotBecomeAnEmptyHistory() {
        val pending = setOf("[Agent message pending]")
        val failure = IllegalStateException("数据库不可读")
        runBlocking {
            `when`(dao.getPersistedAgentMessageMarkers("root", pending.toList())).thenThrow(failure)
        }
        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking { dao.findPersistedAgentMessageMarkers("root", pending) }
        }
        assertSame(failure, thrown)
    }

    @Test fun laterBatchFailureDoesNotReturnPartialAcknowledgements() {
        val pending = (0 until 901).map { "[Agent message $it]" }.toSet()
        val batches = pending.chunked(900)
        val failure = IllegalStateException("后续批次读取失败")
        runBlocking {
            `when`(dao.getPersistedAgentMessageMarkers("root", batches[0])).thenReturn(batches[0])
            `when`(dao.getPersistedAgentMessageMarkers("root", batches[1])).thenThrow(failure)
        }
        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking { dao.findPersistedAgentMessageMarkers("root", pending) }
        }
        assertSame(failure, thrown)
    }

    @Test fun cancelledRecoveryDoesNotQueryTheNextBatch() {
        val pending = (0 until 901).map { "[Agent message $it]" }.toSet()
        val batch = pending.take(900)
        runBlocking {
            `when`(dao.getPersistedAgentMessageMarkers("root", batch))
                .thenThrow(CancellationException("取消恢复"))
        }
        assertThrows(CancellationException::class.java) {
            runBlocking { dao.findPersistedAgentMessageMarkers("root", pending) }
        }
        runBlocking { verify(dao).getPersistedAgentMessageMarkers("root", batch) }
        verifyNoMoreInteractions(dao)
    }
}
