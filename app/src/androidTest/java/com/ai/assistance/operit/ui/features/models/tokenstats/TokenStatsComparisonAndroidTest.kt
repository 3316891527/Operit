package com.ai.assistance.operit.ui.features.models.tokenstats

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.stats.TokenStatsInsightsFilter
import com.ai.assistance.operit.data.stats.TokenStatsTimeRange
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 从实际控件触摸入口验证对比弹层，避免只检查回调声明而漏掉不可点击状态。 */
@RunWith(AndroidJUnit4::class)
class TokenStatsComparisonAndroidTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun tappingCompareOpensDialogAndAppliesPreviousPeriod() {
        val start = LocalDate.of(2026, 11, 19).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 11, 26).atStartOfDay(zone).toInstant().toEpochMilli()
        val range = TokenStatsTimeRange(start, end)
        var applied: TokenStatsTimeRange? = null
        var applyCalled = false
        compose.setContent {
            MaterialTheme {
                TokenStatsTheme {
                    TokenStatsPeriodControls(
                        state = TokenStatsUiState(loading = false, filter = TokenStatsInsightsFilter(range = range)),
                        zone = zone,
                        onDate = {},
                        onComparison = { applied = it; applyCalled = true },
                        onFilter = {},
                        onQuick = {},
                        onRange = { _, _ -> true },
                        onResetFilters = {},
                    )
                }
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.token_insights_compare))
            .performTouchInput { click() }
        compose.onNodeWithText(context.getString(R.string.token_stats_design_compare_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.token_insights_previous_period)).performClick()
        compose.onNodeWithText(context.getString(R.string.token_stats_design_compare_apply)).performClick()
        compose.runOnIdle {
            assertTrue(applyCalled)
            assertEquals(TokenStatsTimeRange(start - range.durationMs, start), applied)
        }
        compose.onNodeWithText(context.getString(R.string.token_stats_design_compare_title)).assertDoesNotExist()
    }

    @Test
    fun allTimeCompareShowsDateSelectionInsteadOfIgnoringTap() {
        var dateOpened = false
        var applyCalled = false
        compose.setContent {
            MaterialTheme {
                TokenStatsTheme {
                    TokenStatsPeriodControls(
                        state = TokenStatsUiState(loading = false, filter = TokenStatsInsightsFilter(range = null)),
                        zone = zone,
                        onDate = { dateOpened = true },
                        onComparison = { applyCalled = true },
                        onFilter = {},
                        onQuick = {},
                        onRange = { _, _ -> true },
                        onResetFilters = {},
                    )
                }
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.token_insights_compare))
            .performTouchInput { click() }
        compose.onNodeWithText(context.getString(R.string.token_stats_design_compare_needs_range)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.token_stats_design_compare_select_dates)).performClick()
        compose.runOnIdle {
            assertTrue(dateOpened)
            assertFalse(applyCalled)
        }
    }
}
