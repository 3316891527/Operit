package com.ai.assistance.operit.ui.features.extensions.packages.market

import com.ai.assistance.operit.data.api.MarketV2Comment
import com.ai.assistance.operit.data.api.MarketV2CommentsPageResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketCommentPaginationTest {
    @Test
    fun pageCountCoversBoundariesWithoutOverflow() {
        assertEquals(0, commentPageCount(0, 50))
        assertEquals(1, commentPageCount(1, 50))
        assertEquals(1, commentPageCount(50, 50))
        assertEquals(2, commentPageCount(51, 50))
        assertEquals(2, commentPageCount(66, 50))
        assertEquals(3, commentPageCount(101, 50))
        assertEquals(5, commentPageCount(101, 25))
        assertEquals(42_949_673, commentPageCount(Int.MAX_VALUE, 50))
        assertEquals(Int.MAX_VALUE, commentPageCount(Int.MAX_VALUE, 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroPageSizeIsRejected() {
        commentPageCount(1, 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativePageSizeIsRejected() {
        commentPageCount(1, -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeTotalIsRejected() {
        commentPageCount(-1, 50)
    }

    @Test
    fun allPagesIncludeCommentsBeyondTheFirstFifty() = runBlocking {
        val all = (1..66).map { MarketV2Comment(id = it.toString()) }
        val requestedPages = mutableListOf<Int>()
        val result = loadAllMarketCommentPages("entry") { id, page ->
            assertEquals("entry", id)
            requestedPages += page
            Result.success(MarketV2CommentsPageResponse(
                total = 66,
                page = page,
                pageSize = 50,
                items = all.drop((page - 1) * 50).take(50),
            ))
        }
        assertEquals(listOf(1, 2), requestedPages)
        assertEquals(all, result.getOrThrow())
    }

    @Test
    fun repeatedIdsKeepTheFirstCommentAndServerOrder() = runBlocking {
        val first = MarketV2Comment(id = "1", body = "first")
        val shared = MarketV2Comment(id = "2", body = "original")
        val last = MarketV2Comment(id = "3", body = "last")
        val result = loadAllMarketCommentPages("entry") { _, page ->
            Result.success(MarketV2CommentsPageResponse(
                total = 4,
                pageSize = 2,
                items = if (page == 1) listOf(first, shared)
                    else listOf(shared.copy(body = "duplicate"), last),
            ))
        }
        assertEquals(listOf(first, shared, last), result.getOrThrow())
    }

    @Test
    fun firstPageFailureDoesNotFetchMorePages() = runBlocking {
        val failure = IllegalStateException("page one failed")
        val requestedPages = mutableListOf<Int>()
        val result = loadAllMarketCommentPages("entry") { _, page ->
            requestedPages += page
            Result.failure(failure)
        }
        assertEquals(listOf(1), requestedPages)
        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    fun laterFailureReturnsNoPartialListAndStopsFetching() = runBlocking {
        val failure = IllegalStateException("page two failed")
        val requestedPages = mutableListOf<Int>()
        val result = loadAllMarketCommentPages("entry") { _, page ->
            requestedPages += page
            if (page == 1) Result.success(MarketV2CommentsPageResponse(
                total = 3,
                pageSize = 1,
                items = listOf(MarketV2Comment(id = "1")),
            )) else Result.failure(failure)
        }
        assertEquals(listOf(1, 2), requestedPages)
        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    fun emptyCommentsOnlyReadTheFirstPage() = runBlocking {
        val requestedPages = mutableListOf<Int>()
        val result = loadAllMarketCommentPages("entry") { _, page ->
            requestedPages += page
            Result.success(MarketV2CommentsPageResponse())
        }
        assertEquals(listOf(1), requestedPages)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun oneFullPageDoesNotRequestANonexistentSecondPage() = runBlocking {
        val comments = (1..50).map { MarketV2Comment(id = it.toString()) }
        val requestedPages = mutableListOf<Int>()
        val result = loadAllMarketCommentPages("entry") { _, page ->
            requestedPages += page
            Result.success(MarketV2CommentsPageResponse(total = 50, items = comments))
        }
        assertEquals(listOf(1), requestedPages)
        assertEquals(comments, result.getOrThrow())
    }
}
