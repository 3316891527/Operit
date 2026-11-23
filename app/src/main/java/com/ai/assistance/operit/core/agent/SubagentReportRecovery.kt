package com.ai.assistance.operit.core.agent

import kotlinx.coroutines.withTimeoutOrNull

/** 把执行期限的一部分留给报告，补写不重新执行工具，也不能延长总期限。 */
internal data class SubagentReportBudget(val totalMs: Long) {
    init { require(totalMs >= 2) }
    val reportMs: Long = minOf(120_000L, (totalMs / 5).coerceAtLeast(1L))
    val workMs: Long = totalMs - reportMs
}

internal const val SUBAGENT_MISSING_REPORT = "The subagent ended without a final report"
internal const val SUBAGENT_REPORT_ATTEMPTS = 2

/** 只捕获自身的时间窗口；用户停止、父任务取消和真正执行错误必须继续向外传播。 */
internal suspend fun recoverSubagentReport(
    budget: SubagentReportBudget,
    reportOnly: Boolean = false,
    work: suspend () -> String,
    report: suspend (attempt: Int) -> String,
): String {
    if (!reportOnly) {
        val first = withTimeoutOrNull(budget.workMs) { work() }
        if (!first.isNullOrBlank()) return first
    }
    return withTimeoutOrNull(budget.reportMs) {
        repeat(SUBAGENT_REPORT_ATTEMPTS) { index ->
            val next = withTimeoutOrNull((budget.reportMs / SUBAGENT_REPORT_ATTEMPTS).coerceAtLeast(1L)) {
                report(index + 1)
            }
            if (!next.isNullOrBlank()) return@withTimeoutOrNull next
        }
        ""
    }.orEmpty()
}
