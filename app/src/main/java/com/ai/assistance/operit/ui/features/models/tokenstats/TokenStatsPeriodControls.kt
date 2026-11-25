package com.ai.assistance.operit.ui.features.models.tokenstats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.stats.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
internal fun TokenStatsPeriodControls(
    state: TokenStatsUiState, zone: ZoneId, onDate: () -> Unit, onComparison: (TokenStatsTimeRange?) -> Unit, onFilter: () -> Unit,
    onQuick: (TokenStatsQuickRange) -> Unit, onRange: (Long, Long) -> Boolean, onResetFilters: () -> Unit,
) {
    val p = LocalTokenStatsPalette.current
    val compact = LocalConfiguration.current.screenWidthDp < 370
    val range = state.currentRange
    var comparisonOpen by rememberSaveable { mutableStateOf(false) }
    val endToday = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val nextAllowed = range != null && range.endMs <= endToday && range.durationMs <= endToday - range.endMs
    val activeFilters = buildList {
        state.filter.models?.let { addAll(it) }
        state.filter.configIds?.forEach { add(if (it.isBlank()) stringResource(R.string.token_insights_configuration_unscoped) else state.configurationNames[it] ?: stringResource(R.string.token_insights_configuration_deleted)) }
        state.filter.functionTypes?.forEach { add(insightsFunction(it)) }
        state.filter.executionModels?.let { addAll(it) }
        state.filter.statuses?.forEach { add(insightsStatus(it)) }
        state.filter.peakPeriods?.forEach { add(insightsPeakName(it)) }
    }
    val noSelection = listOf(state.filter.models, state.filter.configIds, state.filter.functionTypes, state.filter.executionModels, state.filter.statuses, state.filter.peakPeriods).any { it != null && it.isEmpty() }
    val count = listOf(state.filter.models, state.filter.configIds, state.filter.functionTypes, state.filter.executionModels, state.filter.statuses, state.filter.peakPeriods).count { it != null }
    Surface(color = p.panel, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, p.line)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClick = onDate).heightIn(min = 46.dp).padding(start = 3.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp), tint = p.accent)
                    StatsText(range?.let { statsRangeLabel(it, zone) } ?: stringResource(R.string.token_insights_all_time), modifier = Modifier.weight(1f, fill = false), size = 12, weight = FontWeight.SemiBold, maxLines = 1)
                    Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(14.dp), tint = p.muted)
                }
                StatsArrow(previous = true, enabled = range != null, onClick = {
                    if (range != null) onRange(range.startMs - range.durationMs, range.startMs)
                })
                StatsArrow(previous = false, enabled = nextAllowed, onClick = {
                    if (range != null && nextAllowed) onRange(range.endMs, range.endMs + range.durationMs)
                })
            }
            StatsDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val options = listOf(TokenStatsQuickRange.TODAY, TokenStatsQuickRange.WEEK, TokenStatsQuickRange.MONTH, TokenStatsQuickRange.THIS_MONTH, TokenStatsQuickRange.ALL)
                val labels = listOf(R.string.token_insights_today, R.string.token_insights_week, R.string.token_insights_month, R.string.token_insights_this_month, R.string.token_insights_all_time)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    options.forEachIndexed { index, value ->
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(7.dp)).background(if (state.quickRange == value) p.accentBackground else Color.Transparent)
                            .clickable { onQuick(value) }.heightIn(min = 35.dp), contentAlignment = Alignment.Center) {
                            StatsText(stringResource(labels[index]), size = 11, color = if (state.quickRange == value) p.accent else p.muted,
                                weight = if (state.quickRange == value) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                        }
                    }
                }
                // 弹层状态由入口直接持有，全部时间也给出可操作的日期提示，避免点击无反馈。
                IconButton(
                    onClick = { comparisonOpen = true },
                    modifier = Modifier.size(35.dp).clip(RoundedCornerShape(9.dp))
                        .border(1.dp, p.line, RoundedCornerShape(9.dp)),
                ) {
                    Box(Modifier.size(17.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.SwapHoriz, stringResource(R.string.token_insights_compare), modifier = Modifier.size(14.dp),
                            tint = if (state.comparisonRange != null) p.accent else p.muted)
                        if (state.comparisonRange != null) Box(Modifier.align(Alignment.TopEnd).size(4.dp).clip(RoundedCornerShape(3.dp)).background(p.teal))
                    }
                }
                Row(Modifier.height(35.dp).clip(RoundedCornerShape(9.dp)).border(1.dp, p.line, RoundedCornerShape(9.dp)).clickable(onClick = onFilter).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp), tint = p.muted)
                    if (!compact) StatsText(stringResource(R.string.token_stats_design_filter_short), size = 11, color = p.muted, maxLines = 1)
                    if (count > 0) StatsText(count.toString(), color = p.accent, size = 9)
                }
            }
            if (count > 0) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (noSelection) StatsText(stringResource(R.string.token_insights_select_none), size = 10, color = p.accent)
                activeFilters.forEach { label ->
                    StatsText(label, modifier = Modifier.border(1.dp, p.line, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 5.dp), size = 10, color = p.accent, maxLines = 1)
                }
                StatsAction(stringResource(R.string.token_insights_reset_filters), onResetFilters)
            }
        }
    }
    if (comparisonOpen) TokenStatsComparisonDialog(
        current = range,
        initial = state.comparisonRange,
        zone = zone,
        onConfirm = { onComparison(it); comparisonOpen = false },
        onSelectRange = { comparisonOpen = false; onDate() },
        onDismiss = { comparisonOpen = false },
    )
}

@Composable
internal fun StatsArrow(previous: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    val p = LocalTokenStatsPalette.current
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(30.dp, 38.dp)) {
        Icon(if (previous) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
            contentDescription = stringResource(if (previous) R.string.token_insights_previous else R.string.token_insights_next),
            modifier = Modifier.size(20.dp), tint = p.muted.copy(alpha = if (enabled) 1f else .33f))
    }
}

internal fun statsRangeLabel(range: TokenStatsTimeRange, zone: ZoneId): String {
    val start = Instant.ofEpochMilli(range.startMs).atZone(zone).toLocalDate()
    val end = Instant.ofEpochMilli(range.endMs - 1L).atZone(zone).toLocalDate()
    return start.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) + " — " + end.format(DateTimeFormatter.ofPattern(if (start.year == end.year) "MM.dd" else "yyyy.MM.dd"))
}

@Composable
internal fun TokenStatsDateDialog(initial: TokenStatsTimeRange?, zone: ZoneId, onConfirm: (Long, Long) -> Boolean, onAll: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalTokenStatsPalette.current
    val today = LocalDate.now(zone)
    var start by remember { mutableStateOf(initial?.let { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() } ?: today.minusDays(6)) }
    var end by remember { mutableStateOf(initial?.let { Instant.ofEpochMilli(it.endMs - 1).atZone(zone).toLocalDate() } ?: today) }
    var picking by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf(false) }
    StatsSheet("DATE RANGE", stringResource(R.string.token_stats_design_date_title), onDismiss, footer = {
        StatsPrimaryAction(stringResource(R.string.token_stats_design_date_apply), {
            if (end.isBefore(start)) error = true
            else {
                val range = customRangeInclusiveEnd(start, end, zone)
                if (onConfirm(range.startMs, range.endMs)) onDismiss() else error = true
            }
        })
    }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Bottom) {
            StatsDateField(stringResource(R.string.token_stats_design_start_date), start, { picking = 0 }, Modifier.weight(1f))
            StatsText("—", modifier = Modifier.padding(bottom = 12.dp), color = p.faint)
            StatsDateField(stringResource(R.string.token_stats_design_end_date), end, { picking = 1 }, Modifier.weight(1f))
        }
        StatsText(stringResource(R.string.token_stats_design_date_zone, zone.id), Modifier.padding(top = 11.dp), color = p.faint, size = 10)
        val presets = listOf(R.string.token_insights_today, R.string.token_insights_yesterday, R.string.token_insights_week, R.string.token_insights_month, R.string.token_insights_this_month, R.string.token_stats_design_last_month, R.string.token_stats_design_ninety_days, R.string.token_insights_all_time)
        Column(Modifier.padding(top = 19.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            presets.chunked(2).forEachIndexed { row, items ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    items.forEachIndexed { column, resource ->
                        val index = row * 2 + column
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(p.panelAlt).border(1.dp, p.line, RoundedCornerShape(10.dp))
                            .clickable {
                                error = false
                                when (index) {
                                    0 -> { start = today; end = today }
                                    1 -> { start = today.minusDays(1); end = start }
                                    2 -> { start = today.minusDays(6); end = today }
                                    3 -> { start = today.minusDays(29); end = today }
                                    4 -> { start = today.withDayOfMonth(1); end = today }
                                    5 -> { start = today.withDayOfMonth(1).minusMonths(1); end = today.withDayOfMonth(1).minusDays(1) }
                                    6 -> { start = today.minusDays(89); end = today }
                                    7 -> onAll()
                                }
                            }.heightIn(min = 43.dp), contentAlignment = Alignment.Center) { StatsText(stringResource(resource), color = p.muted) }
                    }
                }
            }
        }
        if (error) StatsText(stringResource(R.string.token_stats_custom_range_invalid), color = p.red, size = 11)
    }
    picking?.let { field -> StatsDatePicker(if (field == 0) start else end, onSelect = {
        if (field == 0) start = it else end = it
        error = false; picking = null
    }, onDismiss = { picking = null }) }
}

@Composable
internal fun TokenStatsComparisonDialog(current: TokenStatsTimeRange?, initial: TokenStatsTimeRange?, zone: ZoneId, onConfirm: (TokenStatsTimeRange?) -> Unit, onSelectRange: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalTokenStatsPalette.current
    var mode by remember { mutableStateOf(if (initial == null) 0 else if (current != null && initial.endMs == current.startMs) 1 else 2) }
    var start by remember { mutableStateOf(Instant.ofEpochMilli(initial?.startMs ?: current?.let { it.startMs - it.durationMs } ?: System.currentTimeMillis()).atZone(zone).toLocalDate()) }
    var picking by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    val previous = current?.let { TokenStatsTimeRange(it.startMs - it.durationMs, it.startMs) }
    StatsSheet("COMPARISON", stringResource(R.string.token_stats_design_compare_title), onDismiss, footer = {
        if (current == null) {
            StatsPrimaryAction(stringResource(R.string.token_stats_design_compare_select_dates), onSelectRange)
        } else StatsPrimaryAction(stringResource(R.string.token_stats_design_compare_apply), {
            val range = when {
                mode == 0 -> null
                mode == 1 -> previous
                current != null -> start.atStartOfDay(zone).toInstant().toEpochMilli().let { TokenStatsTimeRange(it, it + current.durationMs) }
                else -> null
            }
            if (range != null && insightsComparisonValidity(current, range) != TokenStatsComparisonValidity.AVAILABLE) invalid = true
            else onConfirm(range)
        })
    }) {
        if (current == null) {
            StatsText(stringResource(R.string.token_stats_design_compare_needs_range), size = 12, color = p.muted)
        } else {
            val labels = listOf(R.string.token_insights_comparison_off, R.string.token_insights_previous_period, R.string.token_stats_design_compare_custom_start)
            labels.forEachIndexed { index, label ->
                Row(Modifier.fillMaxWidth().clickable { mode = index; invalid = false }.padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RadioButton(selected = mode == index, onClick = { mode = index; invalid = false }, colors = RadioButtonDefaults.colors(selectedColor = p.accent, unselectedColor = p.line))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatsText(stringResource(label), weight = FontWeight.Medium)
                        if (index == 0) StatsText(stringResource(R.string.token_stats_design_compare_off_note), size = 10, color = p.muted)
                        if (index == 1 && previous != null) StatsText(statsRangeLabel(previous, zone), size = 10, color = p.muted)
                        if (index == 2) StatsText(stringResource(R.string.token_stats_design_compare_note), size = 10, color = p.muted)
                    }
                }
                StatsDivider()
            }
            StatsDateField(stringResource(R.string.token_stats_design_compare_start_date), start, { mode = 2; picking = true }, Modifier.padding(top = 19.dp))
            if (invalid) StatsText(stringResource(R.string.token_insights_comparison_invalid), color = p.red, size = 11)
        }
    }
    if (picking) StatsDatePicker(start, onSelect = { start = it; invalid = false; picking = false }, onDismiss = { picking = false })
}

@Composable
private fun StatsDateField(label: String, value: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalTokenStatsPalette.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        StatsText(label, color = p.muted, size = 11)
        Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(10.dp)).background(p.background)
            .border(1.dp, p.line, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatsText(value.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")), Modifier.weight(1f), size = 14, maxLines = 1)
                Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(14.dp), tint = p.muted)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsDatePicker(initial: LocalDate, onSelect: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val p = LocalTokenStatsPalette.current
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(onDismissRequest = onDismiss, colors = DatePickerDefaults.colors(containerColor = p.panel),
        confirmButton = { StatsAction(stringResource(R.string.token_insights_save), { state.selectedDateMillis?.let { onSelect(datePickerMillisToLocalDate(it)) } }) },
        dismissButton = { StatsAction(stringResource(R.string.token_insights_cancel), onDismiss) }) {
        DatePicker(state = state, colors = DatePickerDefaults.colors(containerColor = p.panel))
    }
}