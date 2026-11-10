package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.util.stream.MutableSharedStream
import com.ai.assistance.operit.util.stream.TextStreamEvent
import com.ai.assistance.operit.util.stream.collectWithRevisionRelay
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SteeredSegmentStreamLifecycleTest {
    @Test fun snapshotsAppendThroughTheSameStream() = runBlocking {
        val segment = SteeredSegmentStream()
        val original = segment.stream
        val received = mutableListOf<String>()
        val reader = launch(start = CoroutineStart.UNDISPATCHED) {
            original.collect { received += it }
        }
        segment.update("first")
        segment.update("first second")
        segment.close()
        withTimeout(2_000) { reader.join() }
        assertSame(original, segment.stream)
        assertEquals("first second", received.joinToString(""))
    }

    @Test fun replacementDropsTheOldSnapshotFromTheProjectedText() = runBlocking {
        val segment = SteeredSegmentStream()
        val events = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        var displayed = ""
        val reader = launch(start = CoroutineStart.UNDISPATCHED) {
            segment.stream.collectWithRevisionRelay(
                eventChannel = events,
                roundStartLength = 0,
                onRollback = { displayed = it },
                onCompletion = { displayed = it },
            ) { displayed += it }
        }
        segment.update("discarded")
        segment.update("replacement")
        segment.close()
        withTimeout(2_000) { reader.join() }
        assertEquals("replacement", displayed)
    }

    @Test fun closingAnEmptySegmentFinishesItsReader() = runBlocking {
        val segment = SteeredSegmentStream()
        var received = ""
        val reader = launch(start = CoroutineStart.UNDISPATCHED) {
            segment.stream.collect { received += it }
        }
        segment.close()
        segment.close()
        withTimeout(2_000) { reader.join() }
        assertEquals("", received)
    }
}
