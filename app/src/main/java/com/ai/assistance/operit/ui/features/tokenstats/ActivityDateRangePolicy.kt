package com.ai.assistance.operit.ui.features.tokenstats

import com.ai.assistance.operit.data.stats.TokenActivityViewMode
import com.ai.assistance.operit.data.stats.TokenStatsTimeRange
import com.ai.assistance.operit.data.stats.TokenStatsTimeRanges
import java.time.LocalDate
import java.time.ZoneId
internal fun activityRangeForMode(
    mode: TokenActivityViewMode,
    anchorDate: LocalDate,
    historyStartDate: LocalDate?,
    zone: ZoneId,
): TokenStatsTimeRange? {
    val startDate = when (mode) {
        TokenActivityViewMode.DAILY -> anchorDate
        // Rolling 7-day window anchored at today: the future has not happened yet,
        // so the window reaches backwards from the anchor instead of forwards.
        TokenActivityViewMode.WEEKLY -> anchorDate.minusDays(7L)
        // Rolling one-month window. minusMonths clamps month-end dates automatically
        // (e.g. Mar 31 -> Feb 28/29), so no hard-coded month-length rules are needed.
        TokenActivityViewMode.MONTHLY -> anchorDate.minusMonths(1L)
        // Rolling one-year window. minusYears clamps leap days automatically
        // (e.g. Feb 29 -> Feb 28 in a non-leap year).
        TokenActivityViewMode.YEARLY -> anchorDate.minusYears(1L)
        TokenActivityViewMode.CUMULATIVE -> historyStartDate ?: return null
    }
    if (startDate.isAfter(anchorDate)) return null
    return TokenStatsTimeRanges.customRange(
        startDate.atStartOfDay(zone).toInstant().toEpochMilli(),
        anchorDate.plusDays(1L).atStartOfDay(zone).toInstant().toEpochMilli(),
    )
}
