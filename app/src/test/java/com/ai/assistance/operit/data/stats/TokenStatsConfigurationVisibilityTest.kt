package com.ai.assistance.operit.data.stats

import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.ui.features.models.tokenstats.TokenStatsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenStatsConfigurationVisibilityTest {
    private val currentId = "1d12e88b-ce03-40c8-a576-6c6efe5878ce"
    private val deletedId = "84af65cf-de26-42cb-8996-6c5722d978ef"
    private val detachedId = "__operit_deleted_config__"

    @Test fun configurationMembershipKeepsValidUuidNamesAndFindsDeletedIds() {
        val configNames = mapOf(currentId to deletedId)
        assertEquals(setOf(deletedId), invalidTokenStatsConfigurationIds(
            listOf(currentId, deletedId), configNames.keys,
        ))
        assertTrue(isTokenStatsConfigurationVisible(currentId, configNames.keys, setOf(deletedId)))
    }

    @Test fun cleanupIncludesDeletedLegacyIdsAndDeduplicatesThemAcrossUsageRows() {
        assertEquals(setOf(deletedId, detachedId, "old-imported-config"),
            invalidTokenStatsConfigurationIds(
                listOf(currentId, deletedId, deletedId, detachedId, "old-imported-config", "", "  "),
                setOf(currentId),
            ))
    }

    @Test fun deletedConfigurationsAreHiddenBeforeCleanup() {
        assertFalse(isTokenStatsConfigurationVisible(deletedId, setOf(currentId), emptySet()))
        val hidden = invalidTokenStatsConfigurationIds(listOf(deletedId), setOf(currentId))
        assertFalse(isTokenStatsConfigurationVisible(deletedId, setOf(currentId), hidden))
    }

    @Test fun hiddenIdAppliesAcrossDateAndProviderModelGroups() {
        val hidden = invalidTokenStatsConfigurationIds(listOf(deletedId, currentId), setOf(currentId))
        val januaryConfigurations = listOf(deletedId, currentId, "")
        val februaryConfigurations = listOf(currentId, deletedId, deletedId)
        assertEquals(listOf(currentId), januaryConfigurations.filter {
            isTokenStatsConfigurationVisible(it, setOf(currentId), hidden)
        })
        assertEquals(listOf(currentId), februaryConfigurations.filter {
            isTokenStatsConfigurationVisible(it, setOf(currentId), hidden)
        })
        assertEquals(listOf(deletedId, currentId, ""), januaryConfigurations)
        assertEquals(listOf(currentId, deletedId, deletedId), februaryConfigurations)
    }

    @Test fun restoredConfigurationRemainsHiddenIfItsIdWasPreviouslyHidden() {
        assertFalse(isTokenStatsConfigurationVisible(deletedId, emptySet(), setOf(deletedId)))
        assertFalse(isTokenStatsConfigurationVisible(deletedId, setOf(deletedId), setOf(deletedId)))
    }

    @Test fun unassignedLegacyUsageIsNeverVisibleAndHasNoIdToHide() {
        for (id in listOf("", " ")) {
            assertFalse(isTokenStatsConfigurationVisible(id, setOf(id), emptySet()))
        }
        assertTrue(invalidTokenStatsConfigurationIds(listOf("", "  "), emptySet()).isEmpty())
    }

    @Test fun newlyDeletedConfigurationIsAlsoHiddenWithoutCleanup() {
        val hidden = setOf(deletedId)
        assertFalse(isTokenStatsConfigurationVisible(currentId, emptySet(), hidden))
        val moreHidden = hidden + invalidTokenStatsConfigurationIds(listOf(currentId, deletedId), emptySet())
        assertFalse(isTokenStatsConfigurationVisible(currentId, emptySet(), moreHidden))
        assertFalse(isTokenStatsConfigurationVisible(deletedId, emptySet(), moreHidden))
    }

    @Test fun listRowsAndFilterOptionsExcludeHiddenIdentitiesWithoutChangingTotals() {
        val tokens = TokenStatsTokenAggregate(123L, 1L, 0L, 1L)
        val totals = TokenStatsInsightsTotals(
            requests = 5L, usageFactRequests = 5L, unconfirmedUsageRequests = 0L,
            historicalAggregateRequests = 1L, uncachedInput = tokens, cachedInput = tokens,
            cacheWrite = tokens, output = tokens, totalTokens = tokens,
            cost = TokenStatsCostSummary(PricingCurrency.CNY, 0.01, 0L, 5L, 7.0, emptyMap()),
            statusCounts = emptyMap(), unknownStatusRequests = 1L,
        )
        val current = TokenStatsInsightsConfiguration(currentId, "OPENAI", "model-a", totals)
        val another = current.copy(model = "model-b")
        val rows = listOf(current, another, current.copy(configId = ""),
            current.copy(configId = deletedId), current.copy(configId = detachedId))
        val overview = TokenStatsInsightsOverview(
            totals = totals, costComposition = TokenStatsInsightsCostComposition(0.01, 0.0, 0.0, 0.0, 0.0, 0.0),
            models = emptyList(), configurations = rows, functions = emptyList(),
            peakPeriods = emptyList(), buckets = emptyList(), granularity = TokenStatsInsightsGranularity.DAILY,
            comparison = null,
        )
        val state = TokenStatsUiState(
            overview = overview, catalog = overview,
            configurationNames = mapOf(currentId to "可见配置", deletedId to "已隐藏配置"),
            hiddenConfigurationIds = setOf(deletedId),
        )
        assertEquals(listOf(current, another), state.visibleConfigurations)
        assertEquals(listOf(currentId), state.visibleConfigurationFilterIds)
        assertEquals(rows, state.overview?.configurations)
        assertEquals(totals, state.overview?.totals)
    }

    @Test fun cleanupWithOnlyLiveConfigurationsDoesNotHideAnything() {
        assertTrue(invalidTokenStatsConfigurationIds(listOf(currentId), setOf(currentId)).isEmpty())
        assertTrue(invalidTokenStatsConfigurationIds(emptyList(), emptySet()).isEmpty())
    }
}
