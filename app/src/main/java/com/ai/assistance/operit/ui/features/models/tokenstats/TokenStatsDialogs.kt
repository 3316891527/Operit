package com.ai.assistance.operit.ui.features.models.tokenstats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.stats.*
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

internal fun datePickerMillisToLocalDate(utcMidnightMs: Long): java.time.LocalDate =
    Instant.ofEpochMilli(utcMidnightMs).atZone(java.time.ZoneOffset.UTC).toLocalDate()

internal fun customRangeInclusiveEnd(
    startDate: java.time.LocalDate,
    endDate: java.time.LocalDate,
    zone: ZoneId,
): com.ai.assistance.operit.data.stats.TokenStatsTimeRange {
    require(!endDate.isBefore(startDate)) { "end date must not be before start date" }
    val startMs = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
    val endMs = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return com.ai.assistance.operit.data.stats.TokenStatsTimeRanges.customRange(startMs, endMs)
}

@Composable
internal fun PriceSettingsDialog(
    existing: TokenStatsPriceSetting?,
    initialDraft: TokenStatsPriceDraft,
    configurationName: String?,
    onSave: (TokenStatsPriceDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = initialDraft.scope
    val provider = initialDraft.provider
    val model = initialDraft.model
    val configId = initialDraft.configId.orEmpty()
    var billingMode by remember(existing, initialDraft) {
        mutableStateOf(existing?.billingMode ?: initialDraft.billingMode)
    }
    var currency by remember(existing, initialDraft) {
        mutableStateOf(existing?.currency ?: initialDraft.currency)
    }
    var inputPrice by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(existing?.inputPricePerMillion ?: initialDraft.inputPricePerMillion)
        )
    }
    var cachedInputPrice by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(
                existing?.cachedInputPricePerMillion ?: initialDraft.cachedInputPricePerMillion
            )
        )
    }
    var cacheWritePrice by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(
                existing?.cacheWritePricePerMillion ?: initialDraft.cacheWritePricePerMillion
            )
        )
    }
    var outputPrice by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(existing?.outputPricePerMillion ?: initialDraft.outputPricePerMillion)
        )
    }
    var pricePerRequest by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(existing?.pricePerRequest ?: initialDraft.pricePerRequest)
        )
    }
    var billingMultiplier by remember(existing, initialDraft) {
        mutableStateOf(formatTokenStatsPrice(existing?.billingMultiplier ?: initialDraft.billingMultiplier))
    }
    val billingMultiplierValid = isValidMultiplier(billingMultiplier)
    var peakPricingEnabled by remember(existing, initialDraft) {
        mutableStateOf(existing?.peakPricingEnabled ?: initialDraft.peakPricingEnabled)
    }
    var weekendOffPeakPricingEnabled by remember(existing, initialDraft) {
        mutableStateOf(
            existing?.weekendOffPeakPricingEnabled
                ?: initialDraft.weekendOffPeakPricingEnabled
        )
    }
    var holidayOffPeakPricingEnabled by remember(existing, initialDraft) {
        mutableStateOf(
            existing?.holidayOffPeakPricingEnabled
                ?: initialDraft.holidayOffPeakPricingEnabled
        )
    }
    var advancedRulesExpanded by remember { mutableStateOf(true) }
    var peakExpanded by remember { mutableStateOf(true) }
    var peakPeriods by remember(existing, initialDraft) {
        mutableStateOf(
            (existing?.peakSchedule ?: initialDraft.peakSchedule)
                .ifEmpty { DEFAULT_TOKEN_PEAK_TIME_RANGES }
                .map { TokenPricingRules.formatTimeMinutes(it.startMinute) to TokenPricingRules.formatTimeMinutes(it.endMinute) }
        )
    }
    var peakInputMultiplier by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(existing?.peakInputMultiplier ?: initialDraft.peakInputMultiplier)
        )
    }
    var peakCachedInputMultiplier by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(
                existing?.peakCachedInputMultiplier ?: initialDraft.peakCachedInputMultiplier
            )
        )
    }
    var peakCacheWriteMultiplier by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(
                existing?.peakCacheWriteMultiplier ?: initialDraft.peakCacheWriteMultiplier
            )
        )
    }
    var peakOutputMultiplier by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(existing?.peakOutputMultiplier ?: initialDraft.peakOutputMultiplier)
        )
    }
    var longContextPricingEnabled by remember(existing, initialDraft) {
        mutableStateOf(
            existing?.longContextPricingEnabled ?: initialDraft.longContextPricingEnabled
        )
    }
    var longContextExpanded by remember { mutableStateOf(true) }
    var longContextThreshold by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditableLong(existing?.longContextThreshold ?: initialDraft.longContextThreshold)
        )
    }
    var longContextInputMultiplier by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(
                existing?.longContextInputMultiplier ?: initialDraft.longContextInputMultiplier
            )
        )
    }
    var longContextCachedInputMultiplier by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(
                existing?.longContextCachedInputMultiplier
                    ?: initialDraft.longContextCachedInputMultiplier
            )
        )
    }
    var longContextCacheWriteMultiplier by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(
                existing?.longContextCacheWriteMultiplier
                    ?: initialDraft.longContextCacheWriteMultiplier
            )
        )
    }
    var longContextOutputMultiplier by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(
                existing?.longContextOutputMultiplier ?: initialDraft.longContextOutputMultiplier
            )
        )
    }
    val priceFields =
        if (billingMode == BillingMode.TOKEN) {
            listOf(inputPrice, cachedInputPrice, cacheWritePrice, outputPrice)
        } else {
            listOf(pricePerRequest)
        }
    val allPricesValid =
        priceFields.all { raw ->
            raw.isBlank() ||
                raw.toDoubleOrNull()?.let { it.isFinite() && it >= 0.0 } == true
        }
    val parsedPeakPeriods = parsePeakPeriods(peakPeriods)
    val peakRulesValid =
        !peakPricingEnabled ||
            (parsedPeakPeriods != null &&
                peakPeriods.isNotEmpty() &&
                listOf(
                    peakInputMultiplier,
                    peakCachedInputMultiplier,
                    peakCacheWriteMultiplier,
                    peakOutputMultiplier,
                ).all(::isValidMultiplier))
    val longContextRulesValid =
        !longContextPricingEnabled ||
            (longContextThreshold.toLongOrNull()?.let { it > 0L } == true &&
                listOf(
                    longContextInputMultiplier,
                    longContextCachedInputMultiplier,
                    longContextCacheWriteMultiplier,
                    longContextOutputMultiplier,
                ).all(::isValidMultiplier))
    val advancedRulesValid =
        billingMode != BillingMode.TOKEN || (peakRulesValid && longContextRulesValid)
    val targetValid = scope != TokenStatsPriceScope.CONFIG || configId.isNotBlank()
    StatsSheet(
        eyebrow = "PRICING",
        editor = true,
        title = stringResource(R.string.token_stats_pricing_edit),
        onDismiss = onDismiss,
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatsAction(stringResource(R.string.settings_cancel), onDismiss, Modifier.weight(.6f))
                StatsPrimaryAction(
                    text = stringResource(R.string.token_stats_design_save_recalculate),
                    modifier = Modifier.weight(1f),
                    enabled = targetValid && allPricesValid && advancedRulesValid && billingMultiplierValid,
                    onClick = {
                    val parse = { raw: String -> raw.trim().toDoubleOrNull() }
                    onSave(
                        TokenStatsPriceDraft(
                            scope = scope,
                            provider = provider,
                            model = model,
                            configId = configId.takeIf { scope == TokenStatsPriceScope.CONFIG },
                            billingMode = billingMode,
                            currency = currency,
                            billingMultiplier = requireNotNull(billingMultiplier.toDoubleOrNull()),
                            inputPricePerMillion =
                                if (billingMode == BillingMode.TOKEN) parse(inputPrice) else null,
                            cachedInputPricePerMillion =
                                if (billingMode == BillingMode.TOKEN) {
                                    parse(cachedInputPrice)
                                } else {
                                    null
                                },
                            cacheWritePricePerMillion =
                                if (billingMode == BillingMode.TOKEN) {
                                    parse(cacheWritePrice)
                                } else {
                                    null
                                },
                            outputPricePerMillion =
                                if (billingMode == BillingMode.TOKEN) parse(outputPrice) else null,
                            pricePerRequest =
                                if (billingMode == BillingMode.COUNT) {
                                    parse(pricePerRequest)
                                } else {
                                    null
                                },
                            peakPricingEnabled = peakPricingEnabled,
                            weekendOffPeakPricingEnabled = weekendOffPeakPricingEnabled,
                            holidayOffPeakPricingEnabled = holidayOffPeakPricingEnabled,
                            peakSchedule = parsedPeakPeriods ?: DEFAULT_TOKEN_PEAK_TIME_RANGES,
                            peakInputMultiplier = peakInputMultiplier.toDoubleOrNull() ?: 1.0,
                            peakCachedInputMultiplier = peakCachedInputMultiplier.toDoubleOrNull() ?: 1.0,
                            peakCacheWriteMultiplier = peakCacheWriteMultiplier.toDoubleOrNull() ?: 1.0,
                            peakOutputMultiplier = peakOutputMultiplier.toDoubleOrNull() ?: 1.0,
                            longContextPricingEnabled = longContextPricingEnabled,
                            longContextThreshold = longContextThreshold.toLongOrNull(),
                            longContextInputMultiplier = longContextInputMultiplier.toDoubleOrNull() ?: 1.0,
                            longContextCachedInputMultiplier =
                                longContextCachedInputMultiplier.toDoubleOrNull() ?: 1.0,
                            longContextCacheWriteMultiplier =
                                longContextCacheWriteMultiplier.toDoubleOrNull() ?: 1.0,
                            longContextOutputMultiplier =
                                longContextOutputMultiplier.toDoubleOrNull() ?: 1.0,
                        )
                    )
                    onDismiss()
                    },
                )
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                StatsText(
                    text = "$provider · $model",
                    weight = FontWeight.Medium,
                )
                if (scope == TokenStatsPriceScope.CONFIG) {
                    StatsText(
                        text = configurationName.orEmpty(),
                    )
                }
                StatsDivider()
                StatsSegment(
                    options = listOf(stringResource(R.string.settings_billing_mode_token), stringResource(R.string.settings_billing_mode_count)),
                    selected = if (billingMode == BillingMode.TOKEN) 0 else 1,
                    onSelect = { index ->
                        val selectedMode = if (index == 0) BillingMode.TOKEN else BillingMode.COUNT
                        if (billingMode != selectedMode) {
                            if (selectedMode == BillingMode.TOKEN) pricePerRequest = ""
                            else {
                                inputPrice = ""
                                cachedInputPrice = ""
                                cacheWritePrice = ""
                                outputPrice = ""
                            }
                            billingMode = selectedMode
                        }
                    },
                )
                StatsSegment(
                    options = listOf(stringResource(R.string.token_stats_currency_cny), stringResource(R.string.token_stats_currency_usd)),
                    selected = if (currency == PricingCurrency.CNY) 0 else 1,
                    onSelect = { currency = if (it == 0) PricingCurrency.CNY else PricingCurrency.USD },
                )
                StatsDivider()
                StatsDialogField(
                    value = billingMultiplier,
                    onChange = { billingMultiplier = it },
                    label = stringResource(R.string.token_stats_billing_multiplier),
                    isError = !billingMultiplierValid,
                    modifier = Modifier.fillMaxWidth(),
                )
                StatsText(stringResource(R.string.token_stats_billing_multiplier_note), size = 10, color = LocalTokenStatsPalette.current.muted)
                if (billingMode == BillingMode.TOKEN) {
                    Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    PriceField(
                        label = stringResource(R.string.token_stats_pricing_input),
                        value = inputPrice,
                        modifier = Modifier.weight(1f),
                        onChange = { inputPrice = it },
                    )
                    PriceField(
                        label = stringResource(R.string.token_stats_pricing_cached),
                        value = cachedInputPrice,
                        modifier = Modifier.weight(1f),
                        onChange = { cachedInputPrice = it },
                    )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    PriceField(
                        label = stringResource(R.string.token_stats_pricing_cache_write),
                        value = cacheWritePrice,
                        modifier = Modifier.weight(1f),
                        onChange = { cacheWritePrice = it },
                    )
                    PriceField(
                        label = stringResource(R.string.token_stats_pricing_output),
                        value = outputPrice,
                        modifier = Modifier.weight(1f),
                        onChange = { outputPrice = it },
                    )
                    }
                    StatsDivider()
                    PricingRuleGroup(
                        title = stringResource(R.string.token_stats_advanced_pricing),
                        expanded = advancedRulesExpanded,
                        onExpandedChange = { advancedRulesExpanded = it },
                    ) {
                        PricingRuleSection(
                            title = stringResource(R.string.token_stats_peak_pricing),
                        enabled = peakPricingEnabled,
                        expanded = peakExpanded,
                        onEnabledChange = { peakPricingEnabled = it },
                        onExpandedChange = { peakExpanded = it },
                    ) {
                        PricingRuleSwitch(
                            title = stringResource(R.string.token_stats_weekend_off_peak),
                            checked = weekendOffPeakPricingEnabled,
                            onCheckedChange = { weekendOffPeakPricingEnabled = it },
                        )
                        PricingRuleSwitch(
                            title = stringResource(R.string.token_stats_holiday_off_peak),
                            checked = holidayOffPeakPricingEnabled,
                            onCheckedChange = { holidayOffPeakPricingEnabled = it },
                        )
                        StatsText(
                            text = stringResource(R.string.token_stats_holiday_schedule_hint),
                        )
                        StatsText(
                            text = stringResource(
                                R.string.token_stats_pricing_timezone,
                                TokenPricingRules.PRICING_ZONE.id,
                            ),
                        )
                        StatsText(
                            text = stringResource(R.string.token_stats_peak_periods),
                        )
                        peakPeriods.forEachIndexed { index, period ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                TimeField(
                                    label = stringResource(R.string.token_stats_peak_start),
                                    value = period.first,
                                    onChange = { value ->
                                        peakPeriods = peakPeriods.mapIndexed { itemIndex, item ->
                                            if (itemIndex == index) item.copy(first = value) else item
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                TimeField(
                                    label = stringResource(R.string.token_stats_peak_end),
                                    value = period.second,
                                    onChange = { value ->
                                        peakPeriods = peakPeriods.mapIndexed { itemIndex, item ->
                                            if (itemIndex == index) item.copy(second = value) else item
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                if (peakPeriods.size > 1) {
                                    IconButton(
                                        onClick = {
                                            peakPeriods = peakPeriods.filterIndexed { itemIndex, _ ->
                                                itemIndex != index
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.RemoveCircleOutline,
                                            contentDescription = stringResource(
                                                R.string.token_stats_remove_peak_period,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                        TextButton(
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalTokenStatsPalette.current.accent),
                            onClick = { peakPeriods = peakPeriods + ("09:00" to "12:00") },
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            StatsText(stringResource(R.string.token_stats_add_peak_period))
                        }
                        MultiplierGrid(
                            firstLabel = stringResource(R.string.token_stats_design_input),
                            firstValue = peakInputMultiplier,
                            onFirstChange = { peakInputMultiplier = it },
                            secondLabel = stringResource(R.string.token_insights_cached),
                            secondValue = peakCachedInputMultiplier,
                            onSecondChange = { peakCachedInputMultiplier = it },
                            thirdLabel = stringResource(R.string.token_insights_cache_write),
                            thirdValue = peakCacheWriteMultiplier,
                            onThirdChange = { peakCacheWriteMultiplier = it },
                            fourthLabel = stringResource(R.string.token_insights_output),
                            fourthValue = peakOutputMultiplier,
                            onFourthChange = { peakOutputMultiplier = it },
                        )
                    }
                    PricingRuleSection(
                        title = stringResource(R.string.token_stats_long_context_pricing),
                        enabled = longContextPricingEnabled,
                        expanded = longContextExpanded,
                        onEnabledChange = { longContextPricingEnabled = it },
                        onExpandedChange = { longContextExpanded = it },
                    ) {
                        PriceField(
                            label = stringResource(R.string.token_stats_long_context_threshold),
                            value = longContextThreshold,
                            onChange = { longContextThreshold = it },
                            keyboardType = KeyboardType.Number,
                        )
                        MultiplierGrid(
                            firstLabel = stringResource(R.string.token_stats_design_input),
                            firstValue = longContextInputMultiplier,
                            onFirstChange = { longContextInputMultiplier = it },
                            secondLabel = stringResource(R.string.token_insights_cached),
                            secondValue = longContextCachedInputMultiplier,
                            onSecondChange = { longContextCachedInputMultiplier = it },
                            thirdLabel = stringResource(R.string.token_insights_cache_write),
                            thirdValue = longContextCacheWriteMultiplier,
                            onThirdChange = { longContextCacheWriteMultiplier = it },
                            fourthLabel = stringResource(R.string.token_insights_output),
                            fourthValue = longContextOutputMultiplier,
                            onFourthChange = { longContextOutputMultiplier = it },
                        )
                        StatsText(
                            text = stringResource(R.string.token_stats_long_context_hint),
                        )
                    }
                    }
                } else {
                    PriceField(
                        label = stringResource(R.string.token_stats_pricing_per_request),
                        value = pricePerRequest,
                        onChange = { pricePerRequest = it },
                    )
                }

        }
    }
}

@Composable
private fun PricingRuleGroup(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            StatsText(
                text = title,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onExpandedChange(!expanded) }) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
            }
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun PricingRuleSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        StatsText(
            text = title,
            modifier = Modifier.weight(1f),
        )
        StatsPricingSwitch(checked, onCheckedChange)
    }
}

@Composable
private fun PricingRuleSection(
    title: String,
    enabled: Boolean,
    expanded: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val p = LocalTokenStatsPalette.current
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(13.dp),
        color = p.panel,
        border = androidx.compose.foundation.BorderStroke(1.dp, p.line),
        tonalElevation = 0.dp,
    ) {
        Column {
            androidx.compose.material3.Surface(color = p.panelAlt, tonalElevation = 0.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    StatsText(title, Modifier.weight(1f), size = 12, weight = FontWeight.SemiBold)
                    StatsPricingSwitch(enabled, onEnabledChange)
                    IconButton(enabled = enabled, onClick = { onExpandedChange(!expanded) }, modifier = Modifier.size(24.dp)) {
                        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null, tint = p.muted)
                    }
                }
            }
            if (enabled && expanded) {
                StatsDivider()
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
            }
        }
    }
}

@Composable
private fun MultiplierGrid(
    firstLabel: String,
    firstValue: String,
    onFirstChange: (String) -> Unit,
    secondLabel: String,
    secondValue: String,
    onSecondChange: (String) -> Unit,
    thirdLabel: String,
    thirdValue: String,
    onThirdChange: (String) -> Unit,
    fourthLabel: String,
    fourthValue: String,
    onFourthChange: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            Triple(firstLabel, firstValue, onFirstChange),
            Triple(secondLabel, secondValue, onSecondChange),
            Triple(thirdLabel, thirdValue, onThirdChange),
            Triple(fourthLabel, fourthValue, onFourthChange),
        ).forEach { (label, value, onChange) ->
            StatsDialogField(value, onChange, Modifier.weight(1f), label = label,
                fieldHeight = 38.dp, fontSize = 14, labelSize = 9)
        }
    }
}

@Composable
private fun TimeField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    StatsDialogField(value, onChange, modifier.fillMaxWidth(), label = label, keyboardType = KeyboardType.Text)
}

@Composable
private fun PriceField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    modifier: Modifier = Modifier,
) {
    StatsDialogField(value, onChange, modifier.fillMaxWidth(), label = label, keyboardType = keyboardType, fieldHeight = 47.dp, fontSize = 16)
}

private fun parsePeakPeriods(
    values: List<Pair<String, String>>,
): List<TokenPeakTimeRange>? {
    val result = mutableListOf<TokenPeakTimeRange>()
    values.forEach { (start, end) ->
        val startMinute = TokenPricingRules.parseTimeMinutes(start) ?: return null
        val endMinute = TokenPricingRules.parseTimeMinutes(end) ?: return null
        val period = runCatching { TokenPeakTimeRange(startMinute, endMinute) }.getOrNull()
            ?: return null
        result += period
    }
    return result
}

private fun isValidMultiplier(raw: String): Boolean =
    raw.toDoubleOrNull()?.let { it.isFinite() && it >= 0.0 } == true

private fun formatEditableLong(value: Long?): String = value?.toString().orEmpty()

private fun formatEditablePrice(value: Double?): String =
    value?.let(::formatTokenStatsPrice).orEmpty()

/** 开关按稿件的 38×23 尺寸绘制，同时保留原生开关的无障碍语义。 */
@Composable
private fun StatsPricingSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val p = LocalTokenStatsPalette.current
    Box(
        Modifier.size(38.dp, 23.dp)
            .background(if (checked) p.accent else p.background, RoundedCornerShape(20.dp))
            .border(1.dp, if (checked) p.accent else p.line, RoundedCornerShape(20.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.padding(horizontal = 3.dp).size(15.dp)
            .background(if (checked) Color(0xFF25334E) else p.muted, RoundedCornerShape(15.dp)))
    }
}
