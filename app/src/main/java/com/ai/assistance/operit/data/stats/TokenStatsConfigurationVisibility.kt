package com.ai.assistance.operit.data.stats

/** Configuration existence comes from the live config list, never from an ID-shaped name. */
internal fun invalidTokenStatsConfigurationIds(
    usageConfigIds: Iterable<String>,
    existingConfigIds: Set<String>,
): Set<String> = usageConfigIds.filterTo(linkedSetOf()) {
    it.isNotBlank() && it !in existingConfigIds
}

/** Apply only to configuration detail rows; usage queries and totals keep every identity. */
internal fun isTokenStatsConfigurationVisible(
    configId: String,
    existingConfigIds: Set<String>,
    hiddenConfigIds: Set<String>,
): Boolean = configId.isBlank() || configId in existingConfigIds || configId !in hiddenConfigIds
