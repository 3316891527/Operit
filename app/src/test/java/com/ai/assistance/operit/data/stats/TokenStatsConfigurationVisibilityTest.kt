package com.ai.assistance.operit.data.stats

import org.junit.Assert.*
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

    @Test fun deletedConfigurationsStayVisibleUntilExplicitlyCleared() {
        assertTrue(isTokenStatsConfigurationVisible(deletedId, setOf(currentId), emptySet()))
        val hidden = invalidTokenStatsConfigurationIds(listOf(deletedId), setOf(currentId))
        assertFalse(isTokenStatsConfigurationVisible(deletedId, setOf(currentId), hidden))
    }

    @Test fun hiddenIdAppliesAcrossDateAndProviderModelGroups() {
        val hidden = invalidTokenStatsConfigurationIds(listOf(deletedId, currentId), setOf(currentId))
        val januaryConfigurations = listOf(deletedId, currentId, "")
        val februaryConfigurations = listOf(currentId, deletedId, deletedId)
        assertEquals(listOf(currentId, ""), januaryConfigurations.filter {
            isTokenStatsConfigurationVisible(it, setOf(currentId), hidden)
        })
        assertEquals(listOf(currentId), februaryConfigurations.filter {
            isTokenStatsConfigurationVisible(it, setOf(currentId), hidden)
        })
        assertEquals(listOf(deletedId, currentId, ""), januaryConfigurations)
        assertEquals(listOf(currentId, deletedId, deletedId), februaryConfigurations)
    }

    @Test fun restoredConfigurationIsVisibleEvenWhenItsIdWasPreviouslyHidden() {
        assertFalse(isTokenStatsConfigurationVisible(deletedId, emptySet(), setOf(deletedId)))
        assertTrue(isTokenStatsConfigurationVisible(deletedId, setOf(deletedId), setOf(deletedId)))
    }

    @Test fun unassignedLegacyUsageIsAlwaysVisibleAndCannotBeCleared() {
        for (id in listOf("", " ")) {
            assertTrue(isTokenStatsConfigurationVisible(id, emptySet(), setOf(id)))
        }
        assertTrue(invalidTokenStatsConfigurationIds(listOf("", "  "), emptySet()).isEmpty())
    }

    @Test fun newlyDeletedConfigurationRemainsUntilTheNextCleanup() {
        val hidden = setOf(deletedId)
        assertTrue(isTokenStatsConfigurationVisible(currentId, emptySet(), hidden))
        val moreHidden = hidden + invalidTokenStatsConfigurationIds(listOf(currentId, deletedId), emptySet())
        assertFalse(isTokenStatsConfigurationVisible(currentId, emptySet(), moreHidden))
        assertFalse(isTokenStatsConfigurationVisible(deletedId, emptySet(), moreHidden))
    }

    @Test fun cleanupWithOnlyLiveConfigurationsDoesNotHideAnything() {
        assertTrue(invalidTokenStatsConfigurationIds(listOf(currentId), setOf(currentId)).isEmpty())
        assertTrue(invalidTokenStatsConfigurationIds(emptyList(), emptySet()).isEmpty())
    }
}
