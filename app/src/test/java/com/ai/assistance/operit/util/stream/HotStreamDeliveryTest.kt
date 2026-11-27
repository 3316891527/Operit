package com.ai.assistance.operit.util.stream

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HotStreamDeliveryTest {
    @Test
    fun closingFromFirstObserverDoesNotOvertakeTextForTheSecondObserver() = runBlocking {
        withTimeout(5_000) {
            val stream = MutableSharedStreamImpl<Int>(replay = Int.MAX_VALUE)
            val firstReceived = mutableListOf<Int>()
            val secondReceived = mutableListOf<Int>()
            val firstReader = launch(Dispatchers.Unconfined) {
                stream.collect {
                    firstReceived += it
                    stream.close()
                }
            }
            val secondReader = launch(Dispatchers.Unconfined) {
                stream.collect { secondReceived += it }
            }
            stream.emit(1)
            firstReader.join()
            secondReader.join()
            assertEquals(listOf(1), firstReceived)
            assertEquals(firstReceived, secondReceived)
            assertEquals(firstReceived, stream.replayCache)
            assertEquals(0, stream.subscriptionCount)
        }
    }

    @Test
    fun reentrantEmissionPreservesTheSameOrderForEveryObserver() = runBlocking {
        withTimeout(5_000) {
            val stream = MutableSharedStreamImpl<Int>(replay = Int.MAX_VALUE)
            val firstReceived = mutableListOf<Int>()
            val secondReceived = mutableListOf<Int>()
            val firstReader = launch(Dispatchers.Unconfined) {
                stream.collect {
                    firstReceived += it
                    if (it == 1) stream.emit(2)
                }
            }
            val secondReader = launch(Dispatchers.Unconfined) {
                stream.collect { secondReceived += it }
            }
            stream.emit(1)
            stream.close()
            firstReader.join()
            secondReader.join()
            assertEquals(listOf(1, 2), firstReceived)
            assertEquals(firstReceived, secondReceived)
            assertEquals(firstReceived, stream.replayCache)
        }
    }

    @Test
    fun observerCancellingAnotherObserverDoesNotInterruptEmission() = runBlocking {
        withTimeout(5_000) {
            val stream = MutableSharedStreamImpl<Int>(replay = Int.MAX_VALUE)
            var cancelledReader: kotlinx.coroutines.Job? = null
            val received = mutableListOf<Int>()
            val firstReader = launch(Dispatchers.Unconfined) {
                stream.collect { cancelledReader?.cancel() }
            }
            cancelledReader = launch(Dispatchers.Unconfined) { stream.collect {} }
            val stableReader = launch(Dispatchers.Unconfined) {
                stream.collect { received += it }
            }
            stream.emit(1)
            stream.emit(2)
            stream.close()
            firstReader.join()
            cancelledReader?.join()
            stableReader.join()
            assertEquals(listOf(1, 2), received)
            assertEquals(received, stream.replayCache)
            assertEquals(0, stream.subscriptionCount)
        }
    }

    @Test
    fun closingWithBacklogPreservesTextAndFailureForExistingAndLateReaders() = runBlocking {
        withTimeout(5_000) {
            val stream = MutableSharedStreamImpl<Int>(replay = Int.MAX_VALUE)
            val releaseReader = CompletableDeferred<Unit>()
            val received = mutableListOf<Int>()
            val expected = IOException("upstream failed")
            var observedFailure: Throwable? = null
            val reader = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    stream.collect {
                        received += it
                        releaseReader.await()
                    }
                } catch (failure: IOException) {
                    observedFailure = failure
                }
            }

            stream.emit(0)
            yield()
            stream.emit(1)
            stream.emit(2)
            stream.close(expected)
            assertFalse(reader.isCompleted)
            assertFalse(stream.tryEmit(3))

            val lateReceived = mutableListOf<Int>()
            var lateFailure: Throwable? = null
            try {
                stream.collect { lateReceived += it }
            } catch (failure: IOException) {
                lateFailure = failure
            }
            assertEquals(listOf(0, 1, 2), lateReceived)
            assertSame(expected, lateFailure)

            releaseReader.complete(Unit)
            reader.join()
            assertEquals(lateReceived, received)
            assertSame(expected, observedFailure)
            assertSame(expected, stream.completionCause)
            assertEquals(0, stream.subscriptionCount)
        }
    }

    @Test
    fun concurrentEmitAndCloseDeliverExactlyTheAcceptedReplay() = runBlocking {
        withTimeout(10_000) {
            repeat(64) {
                val stream = MutableSharedStreamImpl<Int>(
                    replay = Int.MAX_VALUE,
                    propagateCompletionCause = false,
                )
                val firstAccepted = CompletableDeferred<Unit>()
                val releaseReader = CompletableDeferred<Unit>()
                val received = mutableListOf<Int>()
                val expected = IOException("closed concurrently")
                val reader = launch(start = CoroutineStart.UNDISPATCHED) {
                    stream.collect {
                        releaseReader.await()
                        received += it
                    }
                }
                val producer = async(Dispatchers.Default) {
                    stream.emit(0)
                    firstAccepted.complete(Unit)
                    repeat(512) { stream.emit(it + 1) }
                }
                val closer = async(Dispatchers.Default) {
                    firstAccepted.await()
                    stream.close(expected)
                }
                producer.await()
                closer.await()
                val accepted = stream.replayCache
                assertTrue(accepted.isNotEmpty())
                assertFalse(stream.tryEmit(-1))

                releaseReader.complete(Unit)
                reader.join()
                val lateReceived = mutableListOf<Int>()
                stream.collect { lateReceived += it }
                assertEquals(accepted, received)
                assertEquals(accepted, lateReceived)
                assertSame(expected, stream.completionCause)
                assertEquals(0, stream.subscriptionCount)
            }
        }
    }

    @Test
    fun cancellingObserversDoesNotInterruptConcurrentProducerOrOtherReaders() = runBlocking {
        withTimeout(10_000) {
            val stream = MutableSharedStreamImpl<Int>(replay = Int.MAX_VALUE)
            val received = mutableListOf<Int>()
            val stableReader = launch(start = CoroutineStart.UNDISPATCHED) {
                stream.collect { received += it }
            }
            val start = CompletableDeferred<Unit>()
            val producer = async(Dispatchers.Default) {
                start.await()
                repeat(32_768) {
                    stream.emit(it)
                    if (it % 128 == 0) yield()
                }
            }
            val observerChurn = async(Dispatchers.Default) {
                start.await()
                repeat(128) {
                    val reader = launch(start = CoroutineStart.UNDISPATCHED) {
                        stream.collect { yield() }
                    }
                    yield()
                    reader.cancelAndJoin()
                }
            }
            start.complete(Unit)
            producer.await()
            observerChurn.await()
            stream.close()
            stableReader.join()
            assertEquals((0 until 32_768).toList(), received)
            assertEquals(received, stream.replayCache)
            assertEquals(0, stream.subscriptionCount)
        }
    }
}
