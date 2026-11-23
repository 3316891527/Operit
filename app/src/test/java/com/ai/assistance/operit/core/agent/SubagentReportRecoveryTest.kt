package com.ai.assistance.operit.core.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SubagentReportRecoveryTest {
    @Test fun successfulWorkDoesNotStartAnExtraRequest() = runBlocking {
        var requests = 0
        val result = recoverSubagentReport(SubagentReportBudget(5_000),
            work = { "已完成，并验证文件内容。" }, report = { requests++; "不应调用" })
        assertEquals("已完成，并验证文件内容。", result)
        assertEquals(0, requests)
    }

    @Test fun missingReportGetsTwoBoundedRequestsWithoutRepeatingWork() = runBlocking {
        var workCalls = 0
        val attempts = mutableListOf<Int>()
        val result = recoverSubagentReport(SubagentReportBudget(5_000), work = { workCalls++; "" },
            report = { attempts += it; if (it == 1) "" else "已产出脚本，构建尚未执行。" })
        assertEquals(1, workCalls)
        assertEquals(listOf(1, 2), attempts)
        assertEquals("已产出脚本，构建尚未执行。", result)
    }

    @Test fun workDeadlineCancelsAndJoinsWorkBeforeReportStarts() = runBlocking {
        var workStopped = false
        val result = recoverSubagentReport(SubagentReportBudget(1_000), work = {
            try { awaitCancellation() } finally { workStopped = true }
        }, report = {
            assertTrue(workStopped)
            "期限内只完成了一部分。"
        })
        assertEquals("期限内只完成了一部分。", result)
    }

    @Test fun aStalledFirstReportLeavesTimeForTheSecond() = runBlocking {
        val attempts = mutableListOf<Int>()
        val result = recoverSubagentReport(SubagentReportBudget(5_000), work = { "" }, report = {
            attempts += it
            if (it == 1) awaitCancellation() else "正文报告"
        })
        assertEquals(listOf(1, 2), attempts)
        assertEquals("正文报告", result)
    }

    @Test fun failedReportAttemptsStopWithoutInventingSuccess() = runBlocking {
        var requests = 0
        assertEquals("", recoverSubagentReport(SubagentReportBudget(5_000), work = { "" }, report = { requests++; "" }))
        assertEquals(2, requests)
    }

    @Test fun explicitReportOnlyFollowupNeverRunsTheWorkBlock() = runBlocking {
        val result = recoverSubagentReport(SubagentReportBudget(5_000), reportOnly = true,
            work = { fail("补报告不能重新执行工作"); "" }, report = { "基于已有记录的报告" })
        assertEquals("基于已有记录的报告", result)
    }

    @Test fun userStopDoesNotStartRecovery() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var reportCalls = 0
        val execution = launch {
            recoverSubagentReport(SubagentReportBudget(5_000), work = {
                started.complete(Unit)
                awaitCancellation()
            }, report = { reportCalls++; "" })
        }
        started.await()
        execution.cancelAndJoin()
        assertEquals(0, reportCalls)
    }

    @Test(expected = IllegalStateException::class)
    fun genuineExecutionFailureIsNotRetriedAsWork() = runBlocking {
        recoverSubagentReport(SubagentReportBudget(5_000), work = { error("执行失败") }, report = { fail("不应调用"); "" })
        Unit
    }

    @Test fun graceIsInsideTheOriginalDeadlineForShortAndLongTasks() {
        assertEquals(120_000L, SubagentReportBudget(20 * 60_000L).reportMs)
        assertEquals(12_000L, SubagentReportBudget(60_000L).reportMs)
        assertEquals(120_000L, SubagentReportBudget(120 * 60_000L).reportMs)
        for (total in listOf(60_000L, 20 * 60_000L, 120 * 60_000L)) {
            val budget = SubagentReportBudget(total)
            assertEquals(total, budget.workMs + budget.reportMs)
        }
    }

    @Test fun perExecutionTimeoutUsesTheConfiguredDefaultOrExplicitOverride() {
        assertEquals(20, subagentExecutionTimeout(null, 20))
        assertEquals(45, subagentExecutionTimeout(45, 20))
        for (invalid in listOf(-1, 0, 121)) {
            try {
                subagentExecutionTimeout(invalid, 20)
                fail("非法期限必须被拒绝")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("1 to 120"))
            }
        }
    }
}