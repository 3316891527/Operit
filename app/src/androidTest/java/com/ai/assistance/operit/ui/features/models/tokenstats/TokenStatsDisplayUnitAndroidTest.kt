package com.ai.assistance.operit.ui.features.models.tokenstats

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.stats.TokenStatsCostSummary
import com.ai.assistance.operit.data.stats.TokenStatsDisplayUnit
import com.ai.assistance.operit.data.stats.TokenStatsInsightsCostComposition
import com.ai.assistance.operit.data.stats.TokenStatsInsightsGranularity
import com.ai.assistance.operit.data.stats.TokenStatsInsightsOverview
import com.ai.assistance.operit.data.stats.TokenStatsInsightsTotals
import com.ai.assistance.operit.data.stats.TokenStatsPreferences
import com.ai.assistance.operit.data.stats.TokenStatsTokenAggregate
import java.io.File
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TokenStatsDisplayUnitAndroidTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun changingSettingsInBothDirectionsRecomposesOverview() {
        val state = mutableStateOf(TokenStatsUiState(overview = overview(), loading = false))
        val editing = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                TokenStatsTheme {
                    TokenStatsOverviewContent(
                        state.value, ZoneId.of("UTC"), onGranularity = {},
                        onRequests = { _, _, _, _ -> }, onModels = {}, onSettings = {}, onRequestSort = {},
                    )
                    if (editing.value) TokenStatsSettingsDialog(
                        state.value,
                        onSave = { _, _, unit, _, _ ->
                            state.value = state.value.copy(tokenDisplayUnit = unit)
                            editing.value = false
                        },
                        onDismiss = { editing.value = false },
                    )
                }
            }
        }
        assertShown("1500.0M")
        chooseUnit(editing, R.string.token_insights_millions, R.string.token_insights_billions)
        assertShown("1.500B")
        compose.onAllNodesWithText("1500.0M").assertCountEquals(0)
        chooseUnit(editing, R.string.token_insights_billions, R.string.token_insights_millions)
        assertShown("1500.0M")
        compose.onAllNodesWithText("1.500B").assertCountEquals(0)
    }

    @Test
    fun preferencesReadBothUnitsFromIsolatedDataStore() = runBlocking {
        val file = File(context.cacheDir, "token-unit-${UUID.randomUUID()}.preferences_pb")
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { file },
        )
        try {
            val writer = TokenStatsPreferences(context, store)
            val reader = TokenStatsPreferences(context, store)
            assertEquals(TokenStatsDisplayUnit.MILLIONS, reader.loadTokenDisplayUnit())
            writer.saveTokenDisplayUnit(TokenStatsDisplayUnit.BILLIONS)
            assertEquals(TokenStatsDisplayUnit.BILLIONS, reader.loadTokenDisplayUnit())
            writer.saveTokenDisplayUnit(TokenStatsDisplayUnit.MILLIONS)
            assertEquals(TokenStatsDisplayUnit.MILLIONS, reader.loadTokenDisplayUnit())
        } finally {
            job.cancelAndJoin()
            file.delete()
        }
        Unit
    }

    private fun chooseUnit(editing: androidx.compose.runtime.MutableState<Boolean>, current: Int, next: Int) {
        compose.runOnIdle { editing.value = true }
        compose.onNodeWithText(context.getString(current)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(next)).performClick()
        compose.onNodeWithText(context.getString(R.string.token_insights_save)).performClick()
    }

    private fun assertShown(amount: String) {
        assertTrue(compose.onAllNodesWithText(amount).fetchSemanticsNodes().isNotEmpty())
    }

    private fun overview(): TokenStatsInsightsOverview {
        val zero = TokenStatsTokenAggregate(0L, 1L, 0L, 1L)
        val tokens = zero.copy(knownSum = 1_500_000_000L)
        val totals = TokenStatsInsightsTotals(
            requests = 1L, usageFactRequests = 1L, unconfirmedUsageRequests = 0L,
            historicalAggregateRequests = 0L, uncachedInput = tokens, cachedInput = zero,
            cacheWrite = zero, output = zero, totalTokens = tokens,
            cost = TokenStatsCostSummary(PricingCurrency.CNY, 0.01, 0L, 1L, 7.0, emptyMap()),
            statusCounts = mapOf("SUCCESS" to 1L), unknownStatusRequests = 0L,
        )
        return TokenStatsInsightsOverview(
            totals = totals,
            costComposition = TokenStatsInsightsCostComposition(0.01, 0.0, 0.0, 0.0, 0.0, 0.0),
            models = emptyList(), configurations = emptyList(), functions = emptyList(),
            peakPeriods = emptyList(), buckets = emptyList(), granularity = TokenStatsInsightsGranularity.DAILY,
            comparison = null,
        )
    }
}
