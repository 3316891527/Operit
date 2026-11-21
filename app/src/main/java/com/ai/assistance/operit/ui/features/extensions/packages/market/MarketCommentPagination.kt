package com.ai.assistance.operit.ui.features.extensions.packages.market

import com.ai.assistance.operit.data.api.MarketV2Comment
import com.ai.assistance.operit.data.api.MarketV2CommentsPageResponse

internal fun commentPageCount(total: Int, pageSize: Int): Int {
    require(total >= 0) { "Comment total must not be negative" }
    require(pageSize > 0) { "Comment page size must be positive" }
    // 用商和余数计算向上取整，避免 total 与 pageSize 相加时溢出。
    return total / pageSize + if (total % pageSize == 0) 0 else 1
}

internal suspend fun loadAllMarketCommentPages(
    entryId: String,
    loadPage: suspend (String, Int) -> Result<MarketV2CommentsPageResponse>,
): Result<List<MarketV2Comment>> {
    val firstPage = loadPage(entryId, 1).getOrElse { return Result.failure(it) }
    val pageCount = commentPageCount(firstPage.total, firstPage.pageSize)
    val comments = firstPage.items.toMutableList()
    // 静态评论按页发布；完整读取成功后才交给调用方替换缓存。
    for (page in 2..pageCount) {
        val response = loadPage(entryId, page).getOrElse { return Result.failure(it) }
        comments += response.items
    }
    return Result.success(comments.distinctBy { it.id })
}
