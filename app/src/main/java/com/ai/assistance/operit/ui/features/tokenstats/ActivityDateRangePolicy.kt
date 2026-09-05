package com.ai.assistance.operit.ui.features.tokenstats

import com.ai.assistance.operit.data.stats.TokenActivityViewMode
import com.ai.assistance.operit.data.stats.TokenStatsTimeRange
import com.ai.assistance.operit.data.stats.TokenStatsTimeRanges
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

internal fun activityRangeForMode(
    mode: TokenActivityViewMode,
    anchorDate: LocalDate,
    historyStartDate: LocalDate?,
    zone: ZoneId,
): TokenStatsTimeRange? {
    // Each periodic mode covers the most recent complete period, because future
    // time has not happened yet and cannot be counted. Weekly aligns to the same
    // weekday as today (e.g. today Saturday -> last Saturday through yesterday,
    // exactly seven days); monthly covers the previous natural month; yearly
    // covers the trailing twelve natural months (this month last year through
    // last month).
    val (startDate, inclusiveEndDate) = when (mode) {
        TokenActivityViewMode.DAILY -> anchorDate to anchorDate
        TokenActivityViewMode.WEEKLY -> {
            val start = anchorDate.minusDays(7L)
            start to anchorDate.minusDays(1L)
        }
        TokenActivityViewMode.MONTHLY -> {
            val previousMonth = YearMonth.from(anchorDate).minusMonths(1L)
            previousMonth.atDay(1) to previousMonth.atEndOfMonth()
        }
        TokenActivityViewMode.YEARLY -> {
            val currentMonth = YearMonth.from(anchorDate)
            val startMonth = currentMonth.minusMonths(12L)
            val endMonth = currentMonth.minusMonths(1L)
            startMonth.atDay(1) to endMonth.atEndOfMonth()
        }
        TokenActivityViewMode.CUMULATIVE -> {
            val start = historyStartDate ?: return null
            start to anchorDate
        }
    }
    if (startDate.isAfter(inclusiveEndDate)) return null
    return TokenStatsTimeRanges.customRange(
        startDate.atStartOfDay(zone).toInstant().toEpochMilli(),
        inclusiveEndDate.plusDays(1L).atStartOfDay(zone).toInstant().toEpochMilli(),
    )
}
