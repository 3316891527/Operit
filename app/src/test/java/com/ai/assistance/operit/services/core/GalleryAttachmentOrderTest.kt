package com.ai.assistance.operit.services.core

import java.io.IOException
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.mock

class GalleryAttachmentOrderTest {
    private fun delegate() = AttachmentDelegate(mock(), mock())

    @Test
    fun slowFirstImageKeepsLaterCopiesAndCommitsInSelectionOrder() = runBlocking {
        withTimeout(5_000) {
            val delegate = delegate()
            val copyingFirst = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val events = Collections.synchronizedList(mutableListOf<String>())
            val first = launch(start = CoroutineStart.UNDISPATCHED) {
                delegate.withOrderedAttachmentDraft {
                    events += "copy:3"
                    copyingFirst.complete(Unit)
                    releaseFirst.await()
                    events += "commit:3"
                }
            }
            copyingFirst.await()
            val second = launch(start = CoroutineStart.UNDISPATCHED) {
                delegate.withOrderedAttachmentDraft {
                    events += "copy:2"
                    events += "commit:2"
                }
            }
            val third = launch(start = CoroutineStart.UNDISPATCHED) {
                delegate.withOrderedAttachmentDraft {
                    events += "copy:1"
                    events += "commit:1"
                }
            }
            assertEquals(listOf("copy:3"), events.toList())
            releaseFirst.complete(Unit)
            joinAll(first, second, third)
            assertEquals(listOf("copy:3", "commit:3", "copy:2", "commit:2", "copy:1", "commit:1"), events.toList())
        }
    }

    @Test
    fun queuedImageAndNestedWorkKeepTheDraftCapturedBeforeWaiting() = runBlocking {
        withTimeout(5_000) {
            val delegate = delegate()
            delegate.setActiveDraftChat("original")
            val copyingFirst = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val first = launch(start = CoroutineStart.UNDISPATCHED) {
                delegate.withOrderedAttachmentDraft {
                    copyingFirst.complete(Unit)
                    releaseFirst.await()
                }
            }
            copyingFirst.await()
            val queuedOwner = async(start = CoroutineStart.UNDISPATCHED) {
                delegate.withOrderedAttachmentDraft {
                    coroutineScope {
                        async {
                            delegate.withAttachmentDraft { delegate.capturedDraftChatId() }
                        }.await()
                    }
                }
            }
            delegate.setActiveDraftChat("new")
            val newOwner = async(start = CoroutineStart.UNDISPATCHED) {
                delegate.withOrderedAttachmentDraft { delegate.capturedDraftChatId() }
            }
            releaseFirst.complete(Unit)
            first.join()
            assertEquals("original", queuedOwner.await())
            assertEquals("new", newOwner.await())
        }
    }

    @Test
    fun cancellingAQueuedImageSkipsItsWorkAndDoesNotBlockTheNextImage() = runBlocking {
        withTimeout(5_000) {
            val delegate = delegate()
            val copyingFirst = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val copied = Collections.synchronizedList(mutableListOf<String>())
            val first = launch(start = CoroutineStart.UNDISPATCHED) {
                delegate.withOrderedAttachmentDraft {
                    copyingFirst.complete(Unit)
                    releaseFirst.await()
                    copied += "first"
                }
            }
            copyingFirst.await()
            val cancelled = launch(start = CoroutineStart.UNDISPATCHED) {
                delegate.withOrderedAttachmentDraft { copied += "cancelled" }
            }
            cancelled.cancelAndJoin()
            val last = launch(start = CoroutineStart.UNDISPATCHED) {
                delegate.withOrderedAttachmentDraft { copied += "last" }
            }
            releaseFirst.complete(Unit)
            joinAll(first, last)
            assertEquals(listOf("first", "last"), copied.toList())
        }
    }

    @Test
    fun aFailedCopyReleasesTheQueueForTheNextImage() = runBlocking {
        withTimeout(5_000) {
            val delegate = delegate()
            val copyingFirst = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val failure = IOException("copy failed")
            var observedFailure: IOException? = null
            val first = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    delegate.withOrderedAttachmentDraft {
                        copyingFirst.complete(Unit)
                        releaseFirst.await()
                        throw failure
                    }
                } catch (e: IOException) {
                    observedFailure = e
                }
            }
            copyingFirst.await()
            val next = async(start = CoroutineStart.UNDISPATCHED) {
                delegate.withOrderedAttachmentDraft { "next image copied" }
            }
            releaseFirst.complete(Unit)
            first.join()
            assertSame(failure, observedFailure)
            assertEquals("next image copied", next.await())
        }
    }
}
