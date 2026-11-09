package com.ai.assistance.operit.data.model.theme

import com.ai.assistance.operit.data.theme.script.*
import org.junit.Assert.*
import org.junit.Test

class ThemeResourceLimitTest {
    private fun exceeded(action: () -> Unit): ThemeResourceLimitException {
        try { action(); fail("Expected a resource limit failure") }
        catch (failure: ThemeResourceLimitException) { return failure }
        error("Unreachable")
    }

    @Test fun everyBudgetAcceptsItsExactLimitAndRejectsOneMore() {
        for (budget in ThemeResourceLimit.values()) {
            budget.check(budget.maximum)
            val failure = exceeded { budget.check(budget.maximum + 1, resource = "assets/photo.jpg") }
            assertEquals(budget, failure.budget)
            assertEquals(budget.maximum, failure.maximum)
            assertEquals(0L, failure.used)
            assertEquals(budget.maximum + 1, failure.incoming)
            assertEquals("assets/photo.jpg", failure.resource)
            assertFalse(failure.incomingIsLowerBound)
            assertTrue(failure.message!!.contains("limit=${budget.maximum}"))
        }
    }

    @Test fun batchBudgetReportsExistingAndCumulativeNewResourcesSeparately() {
        val budget = ThemeResourceLimit.BundleBytes
        val current = budget.maximum - 4096
        budget.check(4096, used = current)
        val failure = exceeded { budget.check(4097, used = current) }
        assertEquals(current, failure.used)
        assertEquals(4097L, failure.incoming)
        assertEquals(budget.maximum + 1, failure.used + failure.incoming)
    }

    @Test fun resourceCountIncludesAllFilesSelectedForTheBatch() {
        ThemeResourceLimit.AssetCount.check(2, used = 46)
        val failure = exceeded { ThemeResourceLimit.AssetCount.check(3, used = 46) }
        assertEquals(48L, failure.maximum)
        assertEquals(46L, failure.used)
        assertEquals(3L, failure.incoming)
    }

    @Test fun pixelFailureIsRelativeToTheOriginalProjectIncludingEarlierBatchImages() {
        val failure = exceeded { ThemeResourceLimit.BitmapPixels.check(2_000_000, used = 40_000_000, resource = "assets/photo.JPG") }
        val original = failure.relativeTo(39_000_000)
        assertEquals(39_000_000L, original.used)
        assertEquals(3_000_000L, original.incoming)
        assertEquals("assets/photo.JPG", original.resource)
        assertEquals(ThemeResourceUnit.Pixels, original.budget.unit)
    }

    @Test fun encodedByteAndDecodedPixelBudgetsKeepTheirOwnUnitsAndLimits() {
        assertEquals(8L * 1024 * 1024, ThemeResourceLimit.AssetBytes.maximum)
        assertEquals(512L * 1024, ThemeResourceLimit.SvgBytes.maximum)
        assertEquals(64L * 1024 * 1024, ThemeResourceLimit.BundleBytes.maximum)
        assertEquals(41_943_040L, ThemeResourceLimit.BitmapPixels.maximum)
        assertEquals(ThemeResourceUnit.Bytes, ThemeResourceLimit.AssetBytes.unit)
        assertEquals(ThemeResourceUnit.Pixels, ThemeResourceLimit.BitmapPixels.unit)
    }
}
