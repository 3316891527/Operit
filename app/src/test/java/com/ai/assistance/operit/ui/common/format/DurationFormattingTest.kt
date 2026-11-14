package com.ai.assistance.operit.ui.common.format

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormattingTest {
    @Test
    fun formatsMessageAndToolDurationsWithUnitsThroughDays() {
        val cases = listOf(
            -1L to "0ms",
            0L to "0ms",
            3L to "3ms",
            999L to "999ms",
            1_000L to "1.00s",
            1_005L to "1.01s",
            10_242L to "10.24s",
            111_315L to "1m51.32s",
            3_661_315L to "1h1m1.32s",
            90_061_315L to "1d1h1m1.32s",
            864_000_000L to "10d0.00s",
        )
        cases.forEach { (millis, expected) ->
            assertEquals("millis=$millis", expected, formatElapsedDuration(millis))
        }
    }

    @Test
    fun roundsBeforeCarryingAcrossMinuteHourAndDayBoundaries() {
        val cases = listOf(
            59_994L to "59.99s",
            59_995L to "1m0.00s",
            60_000L to "1m0.00s",
            3_599_994L to "59m59.99s",
            3_599_995L to "1h0.00s",
            3_600_000L to "1h0.00s",
            86_399_994L to "23h59m59.99s",
            86_399_995L to "1d0.00s",
            86_400_000L to "1d0.00s",
        )
        cases.forEach { (millis, expected) ->
            assertEquals("millis=$millis", expected, formatElapsedDuration(millis))
        }
    }

    @Test
    fun keepsDaysAsTheLargestUnitWithoutOverflowing() {
        assertEquals("106751991167d7h12m55.81s", formatElapsedDuration(Long.MAX_VALUE))
    }
}
