package com.ai.assistance.operit.data.resources.theme

import com.ai.assistance.operit.data.theme.script.ThemeResourceLimit
import com.ai.assistance.operit.data.theme.script.ThemeResourceLimitException
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class ThemeResourceReadLimitTest {
    private fun exceeded(action: () -> Unit): ThemeResourceLimitException {
        try { action(); fail("Expected a bounded-read failure") }
        catch (failure: ThemeResourceLimitException) { return failure }
        error("Unreachable")
    }

    @Test fun emptyAndExactlyFullInputsSucceedIncludingZeroRemainingCapacity() {
        assertArrayEquals(byteArrayOf(), ThemeScriptBundleStore.readLimited(ByteArrayInputStream(byteArrayOf()), 0))
        val bytes = ByteArray(8192) { it.toByte() }
        assertArrayEquals(bytes, ThemeScriptBundleStore.readLimited(ByteArrayInputStream(bytes), bytes.size))
    }

    @Test fun unknownFileSizeReadsOnlyOneBytePastTheCapAndReportsALowerBound() {
        var consumed = 0
        val endless = object : InputStream() {
            override fun read(): Int { consumed++; return 1 }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                consumed += length
                buffer.fill(1, offset, offset + length)
                return length
            }
        }
        val failure = exceeded { ThemeScriptBundleStore.readLimited(endless, 32) }
        assertEquals(33, consumed)
        assertEquals(33L, failure.incoming)
        assertTrue(failure.incomingIsLowerBound)
        assertTrue(failure.message!!.contains("at least 33"))
    }

    @Test fun providerSizeIsUsedWhenItCoversTheObservedBytes() {
        val failure = exceeded {
            ThemeScriptBundleStore.readLimited(ByteArrayInputStream(ByteArray(65)), 64,
                knownSize = 9000, resource = "photo.JPG")
        }
        assertEquals(9000L, failure.incoming)
        assertFalse(failure.incomingIsLowerBound)
        assertEquals("photo.JPG", failure.resource)
    }

    @Test fun smallerStaleProviderSizeCannotHideObservedExcess() {
        val failure = exceeded {
            ThemeScriptBundleStore.readLimited(ByteArrayInputStream(ByteArray(65)), 64, knownSize = 4)
        }
        assertEquals(65L, failure.incoming)
        assertTrue(failure.incomingIsLowerBound)
    }

    @Test fun zipRemainingCapacityReportsTheWholeBundleBudget() {
        val budget = ThemeResourceLimit.BundleBytes
        val used = budget.maximum - 8
        val failure = exceeded {
            ThemeScriptBundleStore.readLimited(ByteArrayInputStream(ByteArray(9)), 8, budget, used = used)
        }
        assertEquals(budget.maximum, failure.maximum)
        assertEquals(used, failure.used)
        assertEquals(9L, failure.incoming)
        assertEquals(budget.maximum + 1, failure.used + failure.incoming)
    }

    @Test fun svgAndSourceReadsKeepTheirSpecificBudgetLabels() {
        for (budget in listOf(ThemeResourceLimit.SvgBytes, ThemeResourceLimit.SourceBytes, ThemeResourceLimit.ManifestBytes)) {
            val failure = exceeded { ThemeScriptBundleStore.readLimited(ByteArrayInputStream(ByteArray(5)), 4, budget) }
            assertEquals(budget, failure.budget)
            assertEquals(4L, failure.maximum)
        }
    }
}
