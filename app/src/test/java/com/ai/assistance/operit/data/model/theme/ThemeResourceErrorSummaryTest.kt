package com.ai.assistance.operit.data.model.theme

import com.ai.assistance.operit.data.theme.script.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class ThemeResourceErrorSummaryTest {
    @Test fun pixelExampleShowsAttemptedTotalIncludingTheNewImage() {
        val failure = ThemeResourceLimitException(ThemeResourceLimit.BitmapPixels, 41_943_040,
            used = 40_000_000, incoming = 2_000_000, resource = "assets/photo.jpg")
        assertEquals("Theme drawing assets exceed the decoded pixel budget (42,000,000/41,943,040)", failure.message)
        assertFalse(failure.message!!.contains("photo.jpg"))
        assertFalse(failure.message!!.contains('\n'))
    }

    @Test fun bundleSizeShowsExistingPlusAllPendingFilesRatherThanTheOldUsage() {
        val mib = 1024L * 1024L
        val failure = ThemeResourceLimitException(ThemeResourceLimit.BundleBytes, 64 * mib,
            used = 63 * mib, incoming = 2 * mib)
        assertEquals("Theme script bundle is too large (65 MiB/64 MiB)", failure.message)
    }

    @Test fun firstFontOverTheSingleFileLimitShowsOnlyFileSizeAndLimit() {
        val mib = 1024L * 1024L
        val failure = ThemeResourceLimitException(ThemeResourceLimit.AssetBytes, 8 * mib,
            used = 0, incoming = 12 * mib, resource = "assets/font.ttf")
        assertEquals("Theme script resource exceeds size limit (12 MiB/8 MiB)", failure.message)
    }

    @Test fun oneByteOverTheLimitCannotLookEqualAfterUnitRounding() {
        val failure = ThemeResourceLimitException(ThemeResourceLimit.AssetBytes, 8_388_608,
            used = 0, incoming = 8_388_609)
        assertEquals("Theme script resource exceeds size limit (8,388,609 B/8,388,608 B)", failure.message)
    }

    @Test fun aBoundedReadMarksOnlyTheAttemptedTotalAsALowerBound() {
        val failure = ThemeResourceLimitException(ThemeResourceLimit.AssetBytes, 8_388_608,
            used = 0, incoming = 8_388_609, incomingIsLowerBound = true)
        assertEquals("Theme script resource exceeds size limit (≥8,388,609 B/8,388,608 B)", failure.message)
    }

    @Test fun resourceCountIncludesTheSelectedFilesWithoutExtraLabels() {
        val failure = ThemeResourceLimitException(ThemeResourceLimit.AssetCount, 96, used = 95, incoming = 2)
        assertEquals("Theme script declares too many assets (97/96)", failure.message)
    }

    @Test fun archiveWrappingRetainsTheSameCompactBudgetFailure() {
        val issue = ThemeResourceLimitException(ThemeResourceLimit.BitmapPixels, 41_943_040,
            used = 40_000_000, incoming = 2_000_000)
        val wrapped = IllegalArgumentException("font.ttf: Invalid archive", IllegalStateException("Validation failed", issue))
        assertSame(issue, wrapped.themeResourceLimitFailure())
        assertNull(IllegalArgumentException("Other error").themeResourceLimitFailure())
    }

    @Test fun englishBannerNumbersDoNotChangeWithTheDeviceLocale() {
        val former = Locale.getDefault()
        try {
            for (locale in listOf(Locale.CHINA, Locale.GERMANY)) {
                Locale.setDefault(locale)
                val issue = ThemeResourceLimitException(ThemeResourceLimit.AssetBytes, 8_388_608,
                    used = 0, incoming = 12_345_678)
                assertEquals("Theme script resource exceeds size limit (11.77 MiB/8 MiB)", issue.message)
            }
        } finally { Locale.setDefault(former) }
    }
}
