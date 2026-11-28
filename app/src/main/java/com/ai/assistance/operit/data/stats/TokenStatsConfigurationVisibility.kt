package com.ai.assistance.operit.data.stats

/** 仅用当前配置 ID 判断存续，不根据配置名称推测。 */
internal fun invalidTokenStatsConfigurationIds(
    usageConfigIds: Iterable<String>,
    existingConfigIds: Set<String>,
): Set<String> = usageConfigIds.filterTo(linkedSetOf()) {
    it.isNotBlank() && it !in existingConfigIds
}

/** 仅控制配置列表的展示，不过滤历史请求、用量或费用。 */
internal fun isTokenStatsConfigurationVisible(
    configId: String,
    existingConfigIds: Set<String>,
    hiddenConfigIds: Set<String>,
): Boolean = configId.isNotBlank() && configId in existingConfigIds && configId !in hiddenConfigIds
